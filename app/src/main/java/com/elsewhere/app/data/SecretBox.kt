package com.elsewhere.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Where the AES key comes from: the Android Keystore on a phone, a throwaway key in tests. */
fun interface SecretKeySource { fun key(): SecretKey }

/** A 256-bit AES key that never leaves the Android Keystore; minted on first use, no unlock needed. */
class KeystoreKeySource @Inject constructor() : SecretKeySource {
    override fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    private companion object { const val ALIAS = "elsewhere.secrets" }
}

/**
 * AES-GCM for the few prefs that are secrets: this phone's keys, the friend list, the session token and API keys.
 * A sealed value is base64 of `iv || ciphertext`; anything that fails to open is treated as legacy plaintext.
 */
@Singleton
class SecretBox @Inject constructor(private val keys: SecretKeySource) {
    private val key: SecretKey by lazy { keys.key() }

    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.getEncoder().encodeToString(c.iv + c.doFinal(plain.toByteArray()))
    }

    fun open(sealed: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(sealed)
        require(bytes.size >= IV_BYTES + TAG_BYTES)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, bytes, 0, IV_BYTES)) }
        String(c.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }.getOrNull()

    /** What was stored before sealing began reads as it is. */
    fun openOrPlain(stored: String): String = open(stored) ?: stored

    companion object {
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16

        /** A box over a fresh in-process key, for tests. */
        fun inMemory(): SecretBox {
            val k = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            return SecretBox { k }
        }
    }
}

/** Re-seals whichever of [keys] still sit in plaintext; a no-op once every one has been sealed. */
suspend fun DataStore<Preferences>.sealLegacy(box: SecretBox, vararg keys: Preferences.Key<String>) {
    val p = data.first()
    val legacy = keys.filter { k -> p[k]?.let { box.open(it) == null } == true }
    if (legacy.isEmpty()) return
    edit { e -> for (k in legacy) e[k]?.let { if (box.open(it) == null) e[k] = box.seal(it) } }
}
