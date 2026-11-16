package com.elsewhere.app.account

import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.Work
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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

/** The profile in the vault's canonical shape, shared with the web: lowercase `units`, `place_id` on a work. */
@Serializable
private data class VaultWork(val lat: Double, val lng: Double, val place_id: String, val name: String? = null, val from: Long, val to: Long?)

@Serializable
private data class VaultProfile(val name: String?, val units: String, val homes: List<Home>, val works: List<VaultWork>)

/** Converts the app's Profile at the vault boundary only; the app's own serializer and DataStore are untouched. */
private object VaultProfileSerializer : KSerializer<Profile> {
    override val descriptor: SerialDescriptor = VaultProfile.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Profile) = encoder.encodeSerializableValue(
        VaultProfile.serializer(),
        VaultProfile(value.name, value.units.name.lowercase(), value.homes, value.works.map { VaultWork(it.lat, it.lng, it.placeId, it.name, it.from, it.to) }),
    )

    /** Accepts what either client has ever written: `km`/`KM`, `place_id`/`placeId`. */
    override fun deserialize(decoder: Decoder): Profile {
        val json = decoder as JsonDecoder
        val root = json.decodeJsonElement().jsonObject
        val works = root["works"]?.jsonArray?.map { w ->
            val o = w.jsonObject
            if ("place_id" in o || "placeId" !in o) o else JsonObject(o - "placeId" + ("place_id" to o.getValue("placeId")))
        }
        val units = root["units"]?.jsonPrimitive?.takeIf { it.isString }?.content?.lowercase()
        val fixed = JsonObject(root + listOfNotNull(units?.let { "units" to JsonPrimitive(it) }, works?.let { "works" to JsonArray(it) }))
        val vp = json.json.decodeFromJsonElement(VaultProfile.serializer(), fixed)
        return Profile(vp.name, Units.valueOf(vp.units.uppercase()), vp.homes, vp.works.map { Work(it.lat, it.lng, it.place_id, it.name, it.from, it.to) })
    }
}

/** The sealed contents: the two private keys as hex, the profile in the canonical vault shape, and the settings subset. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VaultContents(
    @EncodeDefault val v: Int = 1, val signPriv: String, val kxPriv: String,
    @Serializable(with = VaultProfileSerializer::class) val profile: Profile, val settings: VaultSettings? = null,
) {
    fun encode(): String = wire.encodeToString(serializer(), this)

    companion object {
        private val wire = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun decode(s: String): VaultContents = wire.decodeFromString(serializer(), s)
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
