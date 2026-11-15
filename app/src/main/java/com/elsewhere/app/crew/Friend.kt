package com.elsewhere.app.crew

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Grants(val liveCity: Boolean = false, val livePrecise: Boolean = false, val sonar: Boolean = false) {
    /** Any grant at all means this friend can be picked for a share window. */
    val any: Boolean get() = liveCity || livePrecise || sonar
}

/** A paired friend, with everything needed to talk privately and what they are allowed to see. */
@Serializable
data class Friend(
    val deviceId: String,
    val name: String,
    val signPub: String,
    val kxPub: String,
    val pairId: String,
    val pairKey: String,
    val addedAt: Long,
    val grants: Grants = Grants(),
    val mutedUntil: Long? = null,
) {
    val fingerprint: String get() = Crypto.fingerprint(Crypto.b64decode(signPub))
    val initial: String get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    fun muted(now: Long) = (mutedUntil ?: 0L) > now
    val key: ByteArray get() = Crypto.b64decode(pairKey)
}

enum class Grade { city, sonar, precise }

/** What one phone tells a friend; `hit` carries the Sonar id when the sender has just fired. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Ping(
    val type: String = "ping",
    val t: Long,
    val grade: Grade,
    val lat: Double,
    val lng: Double,
    val city: String? = null,
    val habitat: Boolean = false,
    val absenceDays: Int? = null,
    @EncodeDefault val hit: Hit? = null,
) {
    @Serializable data class Hit(val id: String, val t: Long)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
        fun decode(s: String): Ping = json.decodeFromString(serializer(), s)
    }
}

/** The types a room message can be; anything else is ignored. */
sealed interface Message {
    data class Pinged(val from: String, val ping: Ping) : Message
    data class Stopped(val from: String) : Message
    data class Revoked(val from: String) : Message

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        @Serializable private data class Typed(val type: String)

        fun parse(from: String, plaintext: String): Message? = runCatching {
            when (json.decodeFromString(Typed.serializer(), plaintext).type) {
                "ping" -> Pinged(from, Ping.decode(plaintext))
                "stop" -> Stopped(from)
                "revoke" -> Revoked(from)
                else -> null
            }
        }.getOrNull()
    }
}

const val STOP_JSON = """{"type":"stop"}"""
const val REVOKE_JSON = """{"type":"revoke"}"""
