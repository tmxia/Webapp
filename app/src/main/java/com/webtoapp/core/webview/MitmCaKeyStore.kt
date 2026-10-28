package com.webtoapp.core.webview

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec










internal object MitmCaKeyStore {

    private const val MAGIC = "WTAMITM1"
    private const val GCM_TAG_BITS = 128
    private const val KEY_FILE_HEADER = "WTA-CAWRAP1\n"

    
    fun readKey(keyFile: File): ByteArray {
        val bytes = keyFile.readBytes()
        if (bytes.size > KEY_FILE_HEADER.length && bytes.copyOfRange(0, KEY_FILE_HEADER.length).contentEquals(KEY_FILE_HEADER.toByteArray())) {
            val payload = bytes.copyOfRange(KEY_FILE_HEADER.length, bytes.size)
            require(payload.size > 12 + (GCM_TAG_BITS / 8)) { "wrapped CA key too short" }
            val iv = payload.copyOfRange(0, 12)
            val wrapped = payload.copyOfRange(12, payload.size)
            val wrapKey = loadOrCreateWrapKey(File(keyFile.parentFile, "mitm_ca_keywrap.bin"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(wrapped)
        }
        
        return bytes
    }

    fun writeKey(keyFile: File, pkcs8Key: ByteArray) {
        val wrapKey = loadOrCreateWrapKey(File(keyFile.parentFile, "mitm_ca_keywrap.bin"))
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val wrapped = cipher.doFinal(pkcs8Key)
        keyFile.writeBytes(KEY_FILE_HEADER.toByteArray() + iv + wrapped)
    }

    private fun loadOrCreateWrapKey(wrapKeyFile: File): SecretKey {
        if (wrapKeyFile.exists()) {
            val raw = wrapKeyFile.readBytes()
            if (raw.size == 32) return SecretKeySpec(raw, "AES")
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        wrapKeyFile.writeBytes(raw)
        return SecretKeySpec(raw, "AES")
    }
}
