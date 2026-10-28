package com.webtoapp.util

import android.content.Context
import android.net.Uri
import com.webtoapp.data.model.CustomCaCertificate
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Locale
import java.util.UUID

object NetworkTrustStorage {
    private const val CERT_DIR = "custom_ca"
    private const val MAX_CERT_BYTES = 256 * 1024

    





    enum class InvalidReason { PRIVATE_KEY, UNRECOGNIZED }

    class InvalidCertificateException(val reason: InvalidReason) : Exception(reason.name)

    














    fun parseCertificates(bytes: ByteArray): List<X509Certificate> {
        require(bytes.isNotEmpty()) { "Certificate is empty" }
        require(bytes.size <= MAX_CERT_BYTES) { "Certificate is too large" }

        val factory = CertificateFactory.getInstance("X.509")
        val normalized = normalizeEncoding(bytes)

        
        normalized.tryGenerate(factory)?.let { if (it.isNotEmpty()) return it }

        
        
        rewritePemLabels(normalized)?.let { rewritten ->
            rewritten.tryGenerate(factory)?.let { if (it.isNotEmpty()) return it }
        }

        
        armorBase64IfApplicable(normalized)?.let { armored ->
            armored.tryGenerate(factory)?.let { if (it.isNotEmpty()) return it }
        }

        throw InvalidCertificateException(diagnose(normalized))
    }

    




    fun importCertificates(
        context: Context,
        uri: Uri,
        displayNameHint: String? = null
    ): List<CustomCaCertificate> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalArgumentException("Unable to read certificate")

        val certificates = parseCertificates(bytes)
        val dir = certDirectory(context)
        var existingShaPrefixes = dir.listFiles().orEmpty()
            .mapNotNull { it.name.substringAfterLast('_', "").takeIf { s -> s.isNotEmpty() } }
            .toSet()

        val imported = mutableListOf<CustomCaCertificate>()
        certificates.forEachIndexed { index, cert ->
            val encoded = cert.encoded
            val sha256 = encoded.sha256Hex()
            val shaPrefix = sha256.take(12)
            if (shaPrefix in existingShaPrefixes) return@forEachIndexed

            val label = if (certificates.size == 1) displayNameHint else null
            val safeName = sanitizeResourceName(label ?: cert.subjectX500Principal.name)
            val file = dir.resolve("${safeName}_${shaPrefix}.cer")
            file.writeBytes(encoded)
            existingShaPrefixes = existingShaPrefixes + shaPrefix

            imported += CustomCaCertificate(
                id = UUID.randomUUID().toString(),
                displayName = (label?.trim()?.takeIf { it.isNotBlank() }
                    ?: cert.subjectX500Principal.name),
                filePath = file.absolutePath,
                sha256 = sha256
            )
        }
        return imported
    }

    fun validateCertificateFile(path: String): Boolean {
        val file = File(path)
        if (!file.isFile || !file.canRead() || file.length() <= 0L) return false
        return runCatching { parseCertificates(file.readBytes()).isNotEmpty() }.getOrDefault(false)
    }

    fun rawResourceName(index: Int): String = "wta_custom_ca_${index + 1}"

    fun sanitizeResourceName(value: String): String {
        val normalized = value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        val base = normalized.ifBlank { "custom_ca" }
        return if (base.first().isLetter()) base.take(32) else "ca_${base.take(29)}"
    }

    private fun ByteArray.tryGenerate(factory: CertificateFactory): List<X509Certificate>? =
        runCatching {
            factory.generateCertificates(inputStream())
                .filterIsInstance<X509Certificate>()
                .distinctBy { runCatching { it.encoded }.getOrDefault(it) }
        }.getOrNull()

    



    private fun normalizeEncoding(bytes: ByteArray): ByteArray {
        
        if (bytes.size >= 4) {
            val utf32 = when {
                bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
                    bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte() -> "UTF-32BE"
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
                    bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte() -> "UTF-32LE"
                else -> null
            }
            utf32?.let {
                runCatching { Charset.forName(it) }.getOrNull()?.let { cs ->
                    return String(bytes, cs).toByteArray(StandardCharsets.UTF_8)
                }
            }
        }
        
        
        if (bytes.size >= 2) {
            val b0 = bytes[0]; val b1 = bytes[1]
            val isUtf16Bom = (b0 == 0xFE.toByte() && b1 == 0xFF.toByte()) ||
                (b0 == 0xFF.toByte() && b1 == 0xFE.toByte())
            if (isUtf16Bom && bytes.size >= 4 && bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()) {
                
            } else if (isUtf16Bom) {
                return String(bytes, StandardCharsets.UTF_16).toByteArray(StandardCharsets.UTF_8)
            }
        }
        
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size)
        }
        return bytes
    }

    
    private fun rewritePemLabels(bytes: ByteArray): ByteArray? {
        val text = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val hasTrusted = text.contains("-----BEGIN TRUSTED CERTIFICATE-----") ||
            text.contains("-----BEGIN X509 CERTIFICATE-----")
        if (!hasTrusted) return null
        return text
            .replace("-----BEGIN TRUSTED CERTIFICATE-----", "-----BEGIN CERTIFICATE-----")
            .replace("-----END TRUSTED CERTIFICATE-----", "-----END CERTIFICATE-----")
            .replace("-----BEGIN X509 CERTIFICATE-----", "-----BEGIN CERTIFICATE-----")
            .replace("-----END X509 CERTIFICATE-----", "-----END CERTIFICATE-----")
            .toByteArray(StandardCharsets.UTF_8)
    }

    
    private fun armorBase64IfApplicable(bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty()) return null
        val first = bytes[0]
        if (first == 0x30.toByte()) return null          
        if (first == '-'.code.toByte()) return null           
        val text = runCatching { String(bytes, StandardCharsets.US_ASCII) }.getOrNull() ?: return null
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length < 16) return null
        if (!compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return null
        if (compact.count { it == '=' } > 2) return null
        if (compact.length % 4 != 0) return null
        val decoded = runCatching { Base64.getDecoder().decode(compact) }.getOrNull() ?: return null
        if (decoded.isEmpty() || decoded[0] != 0x30.toByte()) return null
        return ("-----BEGIN CERTIFICATE-----\n$compact\n-----END CERTIFICATE-----\n")
            .toByteArray(StandardCharsets.US_ASCII)
    }

    private fun diagnose(bytes: ByteArray): InvalidReason {
        val text = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull()
        if (text != null) {
            val upper = text.uppercase(Locale.US)
            if (upper.contains("BEGIN") && upper.contains("PRIVATE KEY")) return InvalidReason.PRIVATE_KEY
        }
        return InvalidReason.UNRECOGNIZED
    }

    fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun certDirectory(context: Context): File =
        File(context.filesDir, CERT_DIR).apply { mkdirs() }
}
