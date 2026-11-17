package com.elsewhere.app.sync

import com.elsewhere.app.account.VaultBody
import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Units
import com.google.crypto.tink.aead.internal.InsecureNonceChaCha20Poly1305
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalSerializationApi::class)
private val wire = Json { ignoreUnknownKeys = true; explicitNulls = true; coerceInputValues = true }

/** What the worker stores under a key: when it was sealed and the sealed bytes, all opaque to it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncBlob(@EncodeDefault val v: Int = 1, val ts: Long, val nonce: String, val ct: String) {
    fun encode(): String = wire.encodeToString(serializer(), this)
    companion object { fun decode(s: String): SyncBlob = wire.decodeFromString(serializer(), s) }
}

/** One stay as it travels: `start` and `end` in epoch seconds, `id` = sha256 of `source|start`. */
@Serializable
data class WireStay(val id: String, val lat: Double, val lng: Double, val name: String?, val start: Long, val end: Long)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MonthChunk(@EncodeDefault val v: Int = 1, val stays: List<WireStay>) {
    fun encode(): String = wire.encodeToString(serializer(), this)
    companion object { fun decode(s: String): MonthChunk = wire.decodeFromString(serializer(), s) }
}

/** A home window as dates; null `from` is "always" (the app's epoch 0), null `to` is open. */
@Serializable
data class WireHome(val name: String?, val lat: Double, val lng: Double, val from: String?, val to: String?)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WireProfile(@EncodeDefault val v: Int = 1, val ts: Long, val name: String?, val homes: List<WireHome>, val units: String) {
    fun encode(): String = wire.encodeToString(serializer(), this)
    companion object { fun decode(s: String): WireProfile = wire.decodeFromString(serializer(), s) }
}

/** The sync wire format, byte for byte what the web client writes: keys, chunks, the profile, and the vault's v2 seal. */
object Wire {
    const val PROFILE = "profile"
    val KEY_RE = Regex("""^[a-z]_[a-z0-9]{1,16}:\d{4}-\d{2}$|^profile$""")
    private const val VAULT_AAD = "vault"
    private val random = SecureRandom()
    private val month = DateTimeFormatter.ofPattern("yyyy-MM")

    /** This phone's source: the crew device id, which is already base32 of the signing key, lower-cased. */
    fun deviceSource(deviceId: String): String = "d_" + deviceId.take(16).lowercase()

    /** A Timeline export's source, from the sha256 hex of its bytes. */
    fun importSource(sha256hex: String): String = "i_" + sha256hex.take(12).lowercase()

    /** The UTC month a stay starts in. */
    fun monthOf(startSec: Long): String = Instant.ofEpochSecond(startSec).atOffset(ZoneOffset.UTC).format(month)

    fun chunkKey(source: String, month: String): String = "$source:$month"

    fun stayId(source: String, startSec: Long): String = Crypto.sha256hex("$source|$startSec".toByteArray())

    private fun nonce(): ByteArray = ByteArray(12).also { random.nextBytes(it) }

    /** ChaCha20-Poly1305 with the key name as associated data, so a blob cannot be moved under another key. */
    fun seal(key: ByteArray, name: String, plaintext: String, ts: Long, nonce: ByteArray = nonce()): SyncBlob =
        SyncBlob(ts = ts, nonce = Crypto.b64(nonce), ct = Crypto.b64(InsecureNonceChaCha20Poly1305(key).encrypt(nonce, plaintext.toByteArray(), name.toByteArray())))

    /** The plaintext, or a [GeneralSecurityException] when the key or the name is not what sealed it. */
    fun open(key: ByteArray, name: String, blob: SyncBlob): String {
        if (blob.v != 1) throw GeneralSecurityException("blob v${blob.v}")
        val n = Crypto.b64decode(blob.nonce)
        if (n.size != 12) throw GeneralSecurityException("nonce")
        return String(InsecureNonceChaCha20Poly1305(key).decrypt(n, Crypto.b64decode(blob.ct), name.toByteArray()))
    }

    /** The vault under the Drive key: no salt, no KDF, the same plaintext as v1. */
    fun sealVault(key: ByteArray, plaintext: String, iv: ByteArray = nonce()): VaultBody =
        VaultBody(v = 2, salt = null, iv = Crypto.b64(iv), ct = Crypto.b64(InsecureNonceChaCha20Poly1305(key).encrypt(iv, plaintext.toByteArray(), VAULT_AAD.toByteArray())))

    fun openVault(key: ByteArray, body: VaultBody): String {
        if (body.v != 2) throw GeneralSecurityException("vault v${body.v}")
        val iv = Crypto.b64decode(body.iv)
        if (iv.size != 12) throw GeneralSecurityException("iv")
        return String(InsecureNonceChaCha20Poly1305(key).decrypt(iv, Crypto.b64decode(body.ct), VAULT_AAD.toByteArray()))
    }

    fun profile(p: Profile, ts: Long): WireProfile = WireProfile(
        ts = ts, name = p.name, units = if (p.units == Units.MI) "mi" else "km",
        homes = p.homes.map { h -> WireHome(h.name ?: h.city, h.lat, h.lng, if (h.from == 0L) null else date(h.from), h.to?.let(::date)) },
    )

    /**
     * The app's profile from a wire one. Only the wire fields travel, so a home's city and country come from the local home
     * at the same spot when there is one, else the name stands in; the local works stay, the web does not know them.
     */
    fun toProfile(w: WireProfile, local: Profile?): Profile = Profile(
        name = w.name, units = if (w.units == "mi") Units.MI else Units.KM, works = local?.works.orEmpty(),
        homes = w.homes.map { h ->
            val same = local?.homes?.firstOrNull { it.lat == h.lat && it.lng == h.lng }
            Home(h.lat, h.lng, same?.city ?: h.name.orEmpty(), same?.cc.orEmpty(), h.from?.let(::epoch) ?: 0L, h.to?.let(::epoch), if (same != null) same.name else h.name)
        },
    )

    private fun date(epochSec: Long): String = LocalDate.ofEpochDay(Math.floorDiv(epochSec, 86400L)).toString()
    private fun epoch(date: String): Long = LocalDate.parse(date).toEpochDay() * 86400L
}
