package com.webtoapp.core.apkbuilder

import com.webtoapp.data.model.NetworkTrustConfig
import com.webtoapp.util.NetworkTrustStorage
import java.io.File

object NetworkSecurityConfigBuilder {
    fun build(config: NetworkTrustConfig): String {
        
        
        
        
        
        val entries = customRawEntries(config)
        val anchors = buildAnchors(config, entries)
        val cleartext = config.cleartextTrafficPermitted.toString()
        return """
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="$cleartext">
$anchors
    </base-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">10.0.2.2</domain>
$anchors
    </domain-config>
</network-security-config>
        """.trimIndent()
    }

    fun customRawEntries(config: NetworkTrustConfig): List<CustomCaRawEntry> {
        var seq = 0
        return config.customCaCertificates.mapNotNull { certificate ->
            val file = File(certificate.filePath)
            if (!file.isFile || !file.canRead()) return@mapNotNull null
            CustomCaRawEntry(
                resourceName = NetworkTrustStorage.rawResourceName(seq++),
                sourceFile = file
            )
        }
    }

    private fun buildAnchors(
        config: NetworkTrustConfig,
        entries: List<CustomCaRawEntry>
    ): String {
        val certs = mutableListOf<String>()
        if (config.trustSystemCa) certs += """            <certificates src="system" />"""
        if (config.trustUserCa) certs += """            <certificates src="user" />"""
        entries.forEach { entry ->
            certs += """            <certificates src="@raw/${entry.resourceName}" />"""
        }
        val body = if (certs.isEmpty()) {
            """            <certificates src="system" />"""
        } else {
            certs.joinToString("\n")
        }
        return """
        <trust-anchors>
$body
        </trust-anchors>
        """.trimIndent()
    }
}

data class CustomCaRawEntry(
    val resourceName: String,
    val sourceFile: File
)
