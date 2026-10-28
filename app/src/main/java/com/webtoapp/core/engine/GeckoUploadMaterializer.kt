package com.webtoapp.core.engine

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.webtoapp.core.logging.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
















internal object GeckoUploadMaterializer {

    private const val TAG = "GeckoUploadMaterializer"

    const val UPLOAD_TTL_MS: Long = 60L * 60L * 1000L

    private const val MAX_FILE_NAME_LENGTH = 100

    




    fun prepare(
        uris: Array<Uri>,
        uploadsDir: File,
        resolver: ContentResolver?,
        nowMs: Long = System.currentTimeMillis()
    ): Array<Uri> {
        runCatching { purgeStale(uploadsDir, nowMs) }
            .onFailure { AppLogger.w(TAG, "Upload cache purge failed: ${it.message}") }
        return Array(uris.size) { i -> materialize(uris[i], uploadsDir, resolver) }
    }

    
    fun purgeAll(uploadsDir: File) {
        val files = uploadsDir.listFiles() ?: return
        for (file in files) {
            runCatching { file.delete() }
        }
    }

    internal fun materialize(uri: Uri, uploadsDir: File, resolver: ContentResolver?): Uri {
        return when (uri.scheme) {
            "content" -> runCatching { copyToCache(uri, uploadsDir, resolver) }
                .onFailure { failure ->
                    AppLogger.w(TAG, "Content URI not materialized, using as-is (${failure.javaClass.simpleName}): $uri")
                }
                .getOrNull()?.let { Uri.fromFile(it) } ?: uri
            else -> uri
        }
    }

    internal fun purgeStale(uploadsDir: File, nowMs: Long) {
        val files = uploadsDir.listFiles() ?: return
        for (file in files) {
            if (!file.isFile) continue
            
            if (nowMs - file.lastModified() > UPLOAD_TTL_MS) {
                runCatching { file.delete() }
            }
        }
    }

    private fun copyToCache(uri: Uri, uploadsDir: File, resolver: ContentResolver?): File {
        if (!uploadsDir.exists()) uploadsDir.mkdirs()
        if (resolver == null) throw IllegalStateException("No ContentResolver to read $uri")

        val target = uniqueTarget(uploadsDir, sanitizeUploadFileName(displayName(resolver, uri)))
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Provider returned no stream for $uri")
        return target
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> displayNameFrom(cursor) }
        } catch (e: Exception) {
            null
        }
    }

    private fun displayNameFrom(cursor: Cursor): String? {
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0 || !cursor.moveToFirst()) return null
        return try { cursor.getString(index) } catch (e: Exception) { null }
    }

    internal fun sanitizeUploadFileName(raw: String?): String {
        var name = raw?.map { ch ->
            if (ch.isLetterOrDigit()) ch else if (ch == '.' || ch == '-' || ch == '_' || ch == ' ') ch else '_'
        }?.joinToString("")?.trim().orEmpty()

        
        if (name.length > MAX_FILE_NAME_LENGTH) {
            name = "..." + name.takeLast(MAX_FILE_NAME_LENGTH - 3)
        }
        if (name.isEmpty() || name.all { it == '.' }) name = "upload"
        return name
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            target = File(dir, "$stem ($n)$ext")
            if (!target.exists()) return target
            n++
        }
    }
}
