package com.webtoapp.core.webview

import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509Certificate
import java.util.Locale












object CertificateHostnameMatcher {

    fun matches(host: String, cert: X509Certificate): Boolean {
        val normalizedHost = host.trim().lowercase(Locale.ROOT).trimEnd('.').removeSurrounding("[", "]")
        if (normalizedHost.isEmpty()) return false

        
        val isIpv6 = normalizedHost.contains(':')
        val isIpv4 = normalizedHost.isNotEmpty() &&
            normalizedHost.all { it.isDigit() || it == '.' } &&
            normalizedHost.count { it == '.' } == 3
        if (isIpv6 || isIpv4) {
            return matchesIpAddress(normalizedHost, cert)
        }

        val sans = runCatching { cert.subjectAlternativeNames }.getOrNull()
        var sawDnsSan = false
        if (sans != null) {
            for (san in sans) {
                val type = san.getOrNull(0)
                val value = san.getOrNull(1) as? String ?: continue
                when (type) {
                    2 -> { 
                        sawDnsSan = true
                        if (matchesDnsName(normalizedHost, value.trim().lowercase(Locale.ROOT))) return true
                    }
                    7 -> { 
                        if (matchesIpAddress(normalizedHost.removeSurrounding("[", "]"), cert)) return true
                    }
                }
            }
        }

        
        if (sawDnsSan) return false
        return cnValues(cert).any { cn ->
            matchesDnsName(normalizedHost, cn.trim().lowercase(Locale.ROOT).trimEnd('.'))
        }
    }

    private fun matchesDnsName(host: String, pattern: String): Boolean {
        if (pattern.isEmpty() || host.isEmpty()) return false
        if (pattern == host) return true
        if (!pattern.startsWith("*.")) return false
        
        
        val suffix = pattern.substring(1) 
        if (!host.endsWith(suffix)) return false
        val label = host.substring(0, host.length - suffix.length)
        return label.isNotEmpty() && !label.contains('.')
    }

    private fun matchesIpAddress(host: String, cert: X509Certificate): Boolean {
        val sans = runCatching { cert.subjectAlternativeNames }.getOrNull() ?: return false
        for (san in sans) {
            if (san.getOrNull(0) != 7) continue
            when (val value = san.getOrNull(1)) {
                is ByteArray -> {
                    val certAddr = bytesToIpAddressString(value) ?: continue
                    if (certAddr == host) return true
                }
                is String -> {
                    if (value.trim() == host) return true
                }
            }
        }
        return false
    }

    private fun bytesToIpAddressString(bytes: ByteArray): String? = when (bytes.size) {
        4 -> bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
        16 -> bytes.toList().chunked(2).joinToString(":") {
            ((it[0].toInt() and 0xFF) shl 8 or (it[1].toInt() and 0xFF)).toString(16)
        }
        else -> null
    }

    private fun cnValues(cert: X509Certificate): List<String> = runCatching {
        val name = X500Name.getInstance(cert.subjectX500Principal.encoded)
        name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.CN)
            .mapNotNull { rdn -> rdn.first?.value?.toString() }
    }.getOrDefault(emptyList())
}
