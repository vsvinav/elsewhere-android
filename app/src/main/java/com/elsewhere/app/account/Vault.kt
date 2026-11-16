package com.elsewhere.app.account

import com.elsewhere.app.data.Profile
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** What the account service stores: the salt and iv the client chose, and the sealed contents. All base64. */
@Serializable
data class VaultBody(val v: Int = 1, val salt: String, val iv: String, val ct: String) {
    fun encode(): String = wire.encodeToString(serializer(), this)
    companion object {
        private val wire = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(s: String): VaultBody = wire.decodeFromString(serializer(), s)
    }
}

/** The settings that travel with the vault; `units` is the web's lowercase word, the profile's units win on restore. */
@Serializable
data class VaultSettings(val units: String? = null, val relayUrl: String? = null, val accountUrl: String? = null)

/** The sealed contents: the two private keys as hex, the profile as this platform already prints it, and the settings subset. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VaultContents(@EncodeDefault val v: Int = 1, val signPriv: String, val kxPriv: String, val profile: Profile, val settings: VaultSettings? = null) {
    fun encode(): String = wire.encodeToString(serializer(), this)

    companion object {
        private val wire = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /** Reads either client's plaintext: the web writes `units` as "km"/"mi", this app as the enum's name. */
        fun decode(s: String): VaultContents {
            val root = wire.parseToJsonElement(s).jsonObject
            val profile = root["profile"]?.jsonObject
            val units = profile?.get("units")?.jsonPrimitive?.takeIf { it.isString }?.content
            val fixed = if (profile != null && units != null && units != units.uppercase())
                JsonObject(root + ("profile" to JsonObject(profile + ("units" to JsonPrimitive(units.uppercase())))))
            else root
            return wire.decodeFromJsonElement(serializer(), fixed)
        }
    }
}

/**
 * The recovery vault's maths, byte for byte what WebCrypto does on the web: PBKDF2-HMAC-SHA256 at 310k iterations
 * to a 32-byte key, then AES-256-GCM with a 12-byte iv and the 16-byte tag appended.
 */
object Vault {
    const val ITERATIONS = 310_000
    const val MIN_PASSPHRASE = 8
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }
    private fun newIv(): ByteArray = ByteArray(12).also { random.nextBytes(it) }

    /** Slow by design (~0.3–1 s); call on `Dispatchers.Default`. The passphrase is trimmed, as on the web. */
    fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.trim().toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun seal(key: ByteArray, plaintext: String, salt: ByteArray, iv: ByteArray = newIv()): VaultBody {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return VaultBody(salt = b64(salt), iv = b64(iv), ct = b64(cipher.doFinal(plaintext.toByteArray())))
    }

    /** The plaintext, or a [GeneralSecurityException] when the key is not the one that sealed it. */
    fun open(key: ByteArray, body: VaultBody): String {
        if (body.v != 1) throw GeneralSecurityException("vault v${body.v}")
        val iv = unb64(body.iv)
        if (iv.size != 12) throw GeneralSecurityException("iv")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(unb64(body.ct)))
    }

    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
}
