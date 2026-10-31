package com.webtoapp.core.webview

import com.google.gson.annotations.SerializedName
import java.net.URL
















object StaticAssetPack {

    
    const val ASSET_BASE = "static_pack"

    
    const val MANIFEST_PATH = "static_pack/manifest.json"

    
    const val MANIFEST_FILE_NAME = "manifest.json"

    
    const val SCHEMA_VERSION = 1

    





    val DEFAULT_STATIC_EXTENSIONS = setOf(
        "css", "js", "mjs",
        "woff", "woff2", "ttf", "otf", "eot",
        "svg", "ico"
    )

    
    val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "avif", "bmp"
    )

    



    fun normalizeUrl(url: String): String {
        return try {
            val u = URL(url)
            val path = if (u.path.isNullOrEmpty()) "/" else u.path
            val query = u.query?.let { "?$it" } ?: ""
            val port = if (u.port != -1 && u.port != u.defaultPort) ":${u.port}" else ""
            "${u.protocol}://${u.host.lowercase()}$port$path$query"
        } catch (e: Exception) {
            url
        }
    }

    







    fun manifestKey(url: String): String {
        val normalized = normalizeUrl(url)
        val q = normalized.indexOf('?')
        return if (q >= 0) normalized.substring(0, q) else normalized
    }

    
    fun extensionOf(url: String): String {
        val path = try {
            URL(url).path ?: url
        } catch (e: Exception) {
            url
        }
        return path.substringAfterLast('.', "").lowercase()
    }

    



    data class Manifest(
        @SerializedName("version") val version: Int = SCHEMA_VERSION,
        @SerializedName("scrapedAt") val scrapedAt: Long = 0L,
        @SerializedName("baseUrl") val baseUrl: String = "",
        @SerializedName("entries") val entries: Map<String, String> = emptyMap()
    ) {
        


        fun lookup(url: String): String? = if (entries.isEmpty()) null else entries[manifestKey(url)]

        
        fun isExpired(maxAgeDays: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
            if (maxAgeDays <= 0 || scrapedAt <= 0L) return false
            val maxAgeMs = maxAgeDays.toLong() * 24L * 3600L * 1000L
            return nowMs - scrapedAt > maxAgeMs
        }
    }
}
