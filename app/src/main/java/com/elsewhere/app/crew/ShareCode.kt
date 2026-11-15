package com.elsewhere.app.crew

import java.util.Random

/**
 * A pairing code — `PT-XXX-XXXX` in Crockford base32, so no I, L, O or U to misread — valid for a day and
 * spent on first use. The bucket is the relay's lookup key; the secret proves the other side had the code.
 */
data class ShareCode(val code: String, val createdAt: Long) {
    val bucket: String get() = code.substring(3, 6)
    val secret: String get() = code.substring(7, 11)

    fun expired(now: Long): Boolean = now - createdAt >= TTL_MS

    fun remaining(now: Long): String {
        val left = TTL_MS - (now - createdAt)
        return when {
            left <= 0 -> "expired"
            left >= 3_600_000L -> "${(left + 1_799_999L) / 3_600_000L} h"
            else -> "${(left + 59_999L) / 60_000L} min"
        }
    }

    companion object {
        const val TTL_MS = 24 * 3_600_000L
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val PREFIX = "elsewhere://pair/"

        fun create(random: Random, createdAt: Long): ShareCode {
            val chars = CharArray(7) { ALPHABET[random.nextInt(ALPHABET.length)] }
            return ShareCode("PT-" + String(chars, 0, 3) + "-" + String(chars, 3, 4), createdAt)
        }

        /** The canonical `PT-XXX-XXXX`, or null when the text can't be a code. */
        fun parse(raw: String): String? {
            var s = raw.uppercase().filter { it.isLetterOrDigit() }
            if (s.startsWith("PT") && s.length == 9) s = s.substring(2)
            if (s.length != 7) return null
            val mapped = s.map { c -> when (c) { 'I', 'L' -> '1'; 'O' -> '0'; else -> c } }
            if (mapped.any { it !in ALPHABET }) return null
            val body = String(mapped.toCharArray())
            return "PT-" + body.substring(0, 3) + "-" + body.substring(3)
        }

        fun link(code: String): String = PREFIX + code

        fun fromLink(uri: String?): String? {
            if (uri == null || !uri.lowercase().startsWith(PREFIX)) return null
            return parse(uri.substring(PREFIX.length))
        }
    }
}
