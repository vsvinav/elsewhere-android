package com.elsewhere.app.crew

import com.google.crypto.tink.aead.internal.InsecureNonceChaCha20Poly1305
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * This phone's identity: an Ed25519 pair for signing and an X25519 pair for agreeing a key with a friend.
 * Kept as raw bytes; the relay only ever sees `deviceId`, a hash of the signing key.
 */
data class DeviceKeys(val signPriv: ByteArray, val signPub: ByteArray, val kxPriv: ByteArray, val kxPub: ByteArray) {
    val deviceId: String get() = Crypto.deviceId(signPub)

    fun encode(): String = listOf(signPriv, signPub, kxPriv, kxPub).joinToString(".") { Crypto.b64(it) }

    override fun equals(other: Any?) = other is DeviceKeys && encode() == other.encode()
    override fun hashCode() = encode().hashCode()

    companion object {
        fun generate(): DeviceKeys = fromSeeds(ByteArray(32).also { SecureRandom().nextBytes(it) }, X25519.generatePrivateKey())

        /** Both seeds are 32 bytes; the X25519 seed is clamped by the curve maths, as on the web. */
        fun fromSeeds(signSeed: ByteArray, kxSeed: ByteArray): DeviceKeys {
            val sign = Ed25519Sign.KeyPair.newKeyPairFromSeed(signSeed)
            return DeviceKeys(sign.privateKey, sign.publicKey, kxSeed, X25519.publicFromPrivate(kxSeed))
        }

        fun decode(s: String): DeviceKeys {
            val (a, b, c, d) = s.split(".").map { Crypto.b64decode(it) }
            return DeviceKeys(a, b, c, d)
        }
    }
}

/** What crosses the relay: who it is from, the nonce and the ciphertext, all base64 but `from`. */
@Serializable
data class Envelope(val v: Int = 1, val from: String, val n: String, val c: String) {
    fun encode(): String = json.encodeToString(serializer(), this)
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(s: String): Envelope = json.decodeFromString(serializer(), s)
    }
}

/** The maths shared with the web client, byte for byte: ids, the pair key, envelopes and request signatures. */
object Crypto {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val HKDF_INFO = "elsewhere/pair/v1"
    private val KX_INFO = "elsewhere/kx/v1".toByteArray()
    private val random = SecureRandom()

    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    fun sha256hex(b: ByteArray): String = sha256(b).joinToString("") { "%02x".format(it) }

    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun b64decode(s: String): ByteArray = Base64.getDecoder().decode(s)

    /** RFC 4648 base32, upper case, no `=` padding. */
    fun base32nopad(b: ByteArray): String = base32(b, B32)

    /** Crockford's alphabet (no I, L, O, U), for strings people read aloud. */
    fun crockford32(b: ByteArray): String = base32(b, CROCKFORD)

    private fun base32(b: ByteArray, alphabet: String): String {
        val out = StringBuilder((b.size * 8 + 4) / 5)
        var buf = 0; var bits = 0
        for (x in b) {
            buf = (buf shl 8) or (x.toInt() and 0xff); bits += 8
            while (bits >= 5) { bits -= 5; out.append(alphabet[(buf shr bits) and 31]) }
        }
        if (bits > 0) out.append(alphabet[(buf shl (5 - bits)) and 31])
        return out.toString()
    }

    fun deviceId(signPub: ByteArray): String = base32nopad(sha256(signPub)).substring(0, 16)

    /** The two signing keys sorted bytewise then hashed, so either side computes the same id. */
    fun pairId(signPubA: ByteArray, signPubB: ByteArray): String {
        val (lo, hi) = if (compare(signPubA, signPubB) <= 0) signPubA to signPubB else signPubB to signPubA
        return base32nopad(sha256(lo + hi)).substring(0, 16)
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) { val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff); if (d != 0) return d }
        return a.size - b.size
    }

    fun pairKey(myKxPriv: ByteArray, theirKxPub: ByteArray, pairId: String): ByteArray {
        val shared = X25519.computeSharedSecret(myKxPriv, theirKxPub)
        return Hkdf.computeHkdf("HMACSHA256", shared, pairId.toByteArray(), HKDF_INFO.toByteArray(), 32)
    }

    /** ChaCha20-Poly1305 over the plaintext with the pair id as associated data; the nonce is random unless a test fixes it. */
    fun seal(pairKey: ByteArray, pairId: String, plaintext: String, from: String, nonce: ByteArray = ByteArray(12).also { random.nextBytes(it) }): Envelope {
        val ct = InsecureNonceChaCha20Poly1305(pairKey).encrypt(nonce, plaintext.toByteArray(), pairId.toByteArray())
        return Envelope(from = from, n = b64(nonce), c = b64(ct))
    }

    /** The plaintext, or a [GeneralSecurityException] when the key, the pair or the bytes are not what they claim. */
    fun open(pairKey: ByteArray, pairId: String, env: Envelope): String {
        if (env.v != 1) throw GeneralSecurityException("envelope v${env.v}")
        val nonce = b64decode(env.n)
        if (nonce.size != 12) throw GeneralSecurityException("nonce")
        return String(InsecureNonceChaCha20Poly1305(pairKey).decrypt(nonce, b64decode(env.c), pairId.toByteArray()))
    }

    fun requestMessage(method: String, path: String, ts: Long, body: ByteArray): ByteArray =
        (method + "\n" + path + "\n" + ts + "\n" + sha256hex(body)).toByteArray()

    fun signRequest(signPriv: ByteArray, method: String, path: String, ts: Long, body: ByteArray): String =
        b64(Ed25519Sign(signPriv).sign(requestMessage(method, path, ts, body)))

    fun verifyRequest(signPub: ByteArray, method: String, path: String, ts: Long, body: ByteArray, sigB64: String): Boolean =
        runCatching { Ed25519Verify(signPub).verify(b64decode(sigB64), requestMessage(method, path, ts, body)) }.isSuccess

    /** Binds the key-agreement key to the signing identity, so a relay cannot swap it. */
    fun kxSig(signPriv: ByteArray, kxPub: ByteArray): String = b64(Ed25519Sign(signPriv).sign(KX_INFO + kxPub))

    fun verifyKxSig(signPub: ByteArray, kxPub: ByteArray, sigB64: String?): Boolean =
        sigB64 != null && runCatching { Ed25519Verify(signPub).verify(b64decode(sigB64), KX_INFO + kxPub) }.isSuccess

    /** Eight characters a person can read back to confirm a pairing; covers both keys so a swapped kx key would show. */
    fun fingerprint(signPub: ByteArray, kxPub: ByteArray): String = crockford32(sha256(signPub + kxPub)).substring(0, 8)
}
