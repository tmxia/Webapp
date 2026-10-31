package com.webtoapp.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom











internal object EmbeddedKey {

    private const val KEY_BYTES = 32
    private const val MASK_SALT = "wta.embedded-key.v1"

    fun generate(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

    fun encode(key: ByteArray, packageName: String): String {
        require(key.size == KEY_BYTES) { "embedded key must be $KEY_BYTES bytes" }
        return xorMask(key, packageName).toHex()
    }

    fun decode(encoded: String, packageName: String): ByteArray? {
        val raw = runCatching { encoded.hexToBytes() }.getOrNull() ?: return null
        if (raw.size != KEY_BYTES) return null
        return xorMask(raw, packageName)
    }

    private fun xorMask(data: ByteArray, packageName: String): ByteArray {
        val mask = MessageDigest.getInstance("SHA-256")
            .digest("$packageName:$MASK_SALT".toByteArray(Charsets.UTF_8))
        return ByteArray(data.size) { i -> (data[i].toInt() xor mask[i % mask.size].toInt()).toByte() }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
        }
    }
}
