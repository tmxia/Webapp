package com.webtoapp.core.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.webtoapp.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom





















object SharedContentInbox {

    private const val TAG = "SharedContentInbox"

    private val random = SecureRandom()

    fun inboxDir(context: Context): File =
        File(context.cacheDir, ShareReceiveContract.INBOX_DIR_NAME)

    private fun indexFile(context: Context): File =
        File(inboxDir(context), ShareReceiveContract.INDEX_FILE_NAME)

    








    suspend fun acceptIntent(
        context: Context,
        intent: Intent?,
        allowedMimeTypes: Collection<String>
    ): List<SharedItem> = withContext(Dispatchers.IO) {
        if (intent == null || allowedMimeTypes.isEmpty()) return@withContext emptyList()

        
        
        if (isHistoryRelaunchIntent(intent)) return@withContext emptyList()

        val action = intent.action
        val isSingle = action == Intent.ACTION_SEND
        val isMultiple = action == Intent.ACTION_SEND_MULTIPLE
        if (!isSingle && !isMultiple) return@withContext emptyList()

        synchronized(lock) {
            val accepted = mutableListOf<SharedItem>()
            try {
                val streams = if (isMultiple) streamUrisOf(intent) else listOfNotNull(streamUriOf(intent))
                val text = if (isSingle) intent.getStringExtra(Intent.EXTRA_TEXT) else null

                if (streams.isNotEmpty()) {
                    for (uri in streams.take(ShareReceiveContract.MAX_ITEMS)) {
                        persistStream(context, uri, intent.type, allowedMimeTypes)
                            ?.let { accepted += it }
                    }
                }

                
                
                if (accepted.isEmpty() && !text.isNullOrEmpty()) {
                    persistText(text, allowedMimeTypes)?.let { accepted += it }
                }

                if (accepted.isEmpty()) {
                    AppLogger.w(
                        TAG,
                        "Rejected inbound share: action=$action type=${intent.type} " +
                            "streams=${streams.size} allowed=$allowedMimeTypes"
                    )
                } else {
                    val index = readIndex(context).filterNot { it.id in accepted.map(SharedItem::id) }
                    writeIndex(context, (accepted + index).take(ShareReceiveContract.MAX_ITEMS * 2))
                    pruneLocked(context)
                    AppLogger.i(TAG, "Accepted ${accepted.size} shared item(s): ${accepted.map { it.name }}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to accept inbound share", e)
            }
            accepted
        }
    }

    







    suspend fun acceptViewIntent(
        context: Context,
        intent: Intent?
    ): SharedItem? = withContext(Dispatchers.IO) {
        if (intent?.action != Intent.ACTION_VIEW) return@withContext null
        
        
        if (isHistoryRelaunchIntent(intent)) return@withContext null
        val uri = intent.data ?: return@withContext null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "content" && scheme != "file") return@withContext null

        synchronized(lock) {
            try {
                val item = persistStream(
                    context,
                    uri,
                    intent.type,
                    ShareReceiveContract.OPEN_WITH_MIME_TYPES,
                    ShareReceiveContract.OPEN_WITH_EXTENSIONS
                )
                if (item == null) {
                    AppLogger.w(TAG, "Rejected open-with payload: uri=$uri type=${intent.type}")
                } else {
                    val index = readIndex(context).filterNot { it.id == item.id }
                    writeIndex(context, (listOf(item) + index).take(ShareReceiveContract.MAX_ITEMS * 2))
                    pruneLocked(context)
                    AppLogger.i(TAG, "Accepted open-with file: ${item.name} (${item.mimeType}, ${item.size}B)")
                }
                item
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to accept open-with file", e)
                null
            }
        }
    }

    





    internal fun isHistoryRelaunchIntent(intent: Intent?): Boolean =
        intent != null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0

    





    suspend fun consume(context: Context, ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        synchronized(lock) {
            val wanted = ids.toSet()
            val now = System.currentTimeMillis()
            var changed = false
            val updated = readIndex(context).map { item ->
                if (!item.isConsumed && item.id in wanted) {
                    changed = true
                    item.copy(consumedAt = now)
                } else {
                    item
                }
            }
            if (changed) writeIndex(context, updated)
        }
    }

    
    suspend fun pending(context: Context): List<SharedItem> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            pruneLocked(context)
            readIndex(context).filterNot { it.isConsumed }
        }
    }

    



    fun lookup(context: Context, id: String): SharedItem? {
        if (id.isBlank()) return null
        synchronized(lock) {
            return readIndex(context).firstOrNull { it.id == id }
        }
    }

    







    fun readChunk(context: Context, id: String, offset: Int, length: Int): ByteArray? {
        if (id.isBlank()) return null
        synchronized(lock) {
            val item = readIndex(context).firstOrNull { it.id == id } ?: return null
            if (item.isText) return null
            val file = item.path?.let(::File) ?: return null
            val inbox = inboxDir(context).canonicalFile
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
            if (!canonical.path.startsWith(inbox.path + File.separator)) return null
            if (!canonical.isFile) return null
            if (offset < 0 || length <= 0) return ByteArray(0)
            val size = canonical.length()
            if (offset.toLong() >= size) return ByteArray(0)
            val remaining = size - offset
            val toRead = minOf(
                length.toLong(),
                ShareReceiveContract.READ_CHUNK_MAX_BYTES.toLong(),
                remaining
            ).toInt()
            return java.io.RandomAccessFile(canonical, "r").use { raf ->
                raf.seek(offset.toLong())
                val buf = ByteArray(toRead)
                val n = raf.read(buf)
                when {
                    n <= 0 -> ByteArray(0)
                    n == toRead -> buf
                    else -> buf.copyOf(n)
                }
            }
        }
    }

    







    suspend fun findForFileChooser(context: Context, acceptTypes: List<String>?): SharedItem? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                pruneLocked(context)
                readIndex(context).firstOrNull { item ->
                    !item.isConsumed && !item.isText && chooserAccepts(acceptTypes, item.mimeType)
                }
            }
        }

    
    suspend fun claim(context: Context, id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val items = readIndex(context)
            items.filter { it.id == id }.forEach { deletePayload(context, it) }
            writeIndex(context, items.filterNot { it.id == id })
            AppLogger.i(TAG, "Claimed shared item $id for file chooser")
        }
    }

    
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readIndex(context).forEach { deletePayload(context, it) }
            indexFile(context).delete()
            inboxDir(context).listFiles()?.forEach { it.delete() }
            Unit
        }
    }

    



    suspend fun prune(context: Context) = withContext(Dispatchers.IO) {
        synchronized(lock) { pruneLocked(context) }
    }

    
    
    

    private val lock = Any()

    
    private val COMMON_EXTENSIONS = mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/bmp" to "bmp",
        "image/heic" to "heic",
        "image/heif" to "heif",
        "image/avif" to "avif",
        "image/svg+xml" to "svg",
        ShareReceiveContract.MIME_TEXT to "txt"
    )

    
    private fun pruneLocked(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val all = readIndex(context)
            val kept = mutableListOf<SharedItem>()
            var total = 0L

            for (item in all) {
                val expired = now - item.receivedAt > ShareReceiveContract.TTL_MS
                val missing = !item.isText && item.path?.let { !File(it).exists() } == true
                if (expired || missing) {
                    deletePayload(context, item)
                    continue
                }
                total += item.size
                if (total > ShareReceiveContract.MAX_TOTAL_BYTES) {
                    deletePayload(context, item)
                    continue
                }
                kept += item
            }

            if (kept.size != all.size) writeIndex(context, kept)

            
            val referenced = kept.mapNotNull { it.path }.toSet()
            inboxDir(context).listFiles()?.forEach { file ->
                val isIndex = file.name == ShareReceiveContract.INDEX_FILE_NAME
                if (!isIndex && file.absolutePath !in referenced) file.delete()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Inbox prune failed: ${e.message}")
        }
    }

    private fun persistStream(
        context: Context,
        uri: Uri,
        declaredType: String?,
        allowedMimeTypes: Collection<String>,
        allowedExtensions: Collection<String> = emptyList()
    ): SharedItem? {
        val mimeType = resolveMimeType(context, uri, declaredType)
        if (!mimeAllowed(allowedMimeTypes, mimeType) && !extensionAllowed(allowedExtensions, uri)) {
            return null
        }

        val resolver = context.contentResolver
        
        
        val length = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (length > ShareReceiveContract.MAX_FILE_BYTES) {
            AppLogger.w(TAG, "Dropped oversize share (${length} bytes): $uri")
            return null
        }

        val id = newId()
        val safeName = sanitizeFileName(displayNameOf(context, uri), id, mimeType)
        val dir = inboxDir(context).apply { mkdirs() }
        val target = File(dir, safeName)

        val copied = try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        written += read
                        if (written > ShareReceiveContract.MAX_FILE_BYTES) {
                            throw IllegalStateException("payload exceeded ${ShareReceiveContract.MAX_FILE_BYTES} bytes")
                        }
                        output.write(buffer, 0, read)
                    }
                    written
                }
            } ?: return null
        } catch (e: Exception) {
            target.delete()
            AppLogger.w(TAG, "Failed to persist shared stream: ${e.message}")
            return null
        }

        return SharedItem(
            id = id,
            kind = ShareReceiveContract.KIND_FILE,
            mimeType = mimeType,
            name = safeName,
            size = copied,
            path = target.absolutePath
        )
    }

    private fun persistText(text: String, allowedMimeTypes: Collection<String>): SharedItem? {
        if (!mimeAllowed(allowedMimeTypes, ShareReceiveContract.MIME_TEXT)) return null
        val trimmed = if (text.length > ShareReceiveContract.MAX_TEXT_CHARS) {
            text.substring(0, ShareReceiveContract.MAX_TEXT_CHARS)
        } else {
            text
        }
        return SharedItem(
            id = newId(),
            kind = ShareReceiveContract.KIND_TEXT,
            mimeType = ShareReceiveContract.MIME_TEXT,
            name = "",
            size = trimmed.toByteArray(Charsets.UTF_8).size.toLong(),
            text = trimmed
        )
    }

    





    internal fun chooserAccepts(acceptTypes: List<String>?, mimeType: String): Boolean {
        if (acceptTypes.isNullOrEmpty()) return true
        if (acceptTypes.any { it.isNullOrBlank() }) return true
        return acceptTypes.any { raw ->
            val accept = raw.trim().lowercase()
            when {
                accept.isEmpty() -> true
                accept == "*/*" -> true
                accept == mimeType.lowercase() -> true
                accept.endsWith("/*") -> mimeType.lowercase().startsWith(accept.removeSuffix("*"))
                
                
                accept.startsWith(".") -> {
                    val extension = extensionForMimeType(mimeType)
                    extension != null && extension == accept.removePrefix(".")
                }
                else -> false
            }
        }
    }

    internal fun mimeAllowed(allowedMimeTypes: Collection<String>, mimeType: String): Boolean {
        val type = mimeType.lowercase()
        return allowedMimeTypes.any { raw ->
            val allowed = raw.trim().lowercase()
            when {
                allowed.isEmpty() -> false
                allowed == type -> true
                allowed.endsWith("/*") -> type.startsWith(allowed.removeSuffix("*"))
                else -> false
            }
        }
    }

    




    internal fun extensionAllowed(allowedExtensions: Collection<String>, uri: Uri): Boolean {
        if (allowedExtensions.isEmpty()) return false
        val ext = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return ext.isNotEmpty() && allowedExtensions.any { it.lowercase() == ext }
    }

    private fun resolveMimeType(context: Context, uri: Uri, declaredType: String?): String {
        if (!declaredType.isNullOrBlank() && declaredType != "*/*") return declaredType
        return try {
            context.contentResolver.getType(uri) ?: guessFromUri(uri)
        } catch (e: Exception) {
            guessFromUri(uri)
        }
    }

    








    internal fun extensionForMimeType(mimeType: String): String? {
        val type = mimeType.lowercase().trim()
        if (type.isEmpty()) return null
        COMMON_EXTENSIONS[type]?.let { return it }
        if (type == "application/octet-stream") return null

        MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.let { return it }

        
        
        val subtype = type.substringAfter('/', "")
            .substringBefore('+')
            .replace(Regex("[^a-z0-9]"), "")
        return subtype.takeIf { it.isNotEmpty() && it.length <= 12 }
    }

    private fun guessFromUri(uri: Uri): String {
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        if (extension.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun displayNameOf(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    





    internal fun sanitizeFileName(raw: String?, id: String, mimeType: String): String {
        val cleaned = raw
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            ?.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]"), "_")
            ?.trim('.', '_')
            ?.take(96)
            .orEmpty()
        val base = if (cleaned.isBlank()) id else cleaned
        if (base.contains('.')) return base
        val extension = extensionForMimeType(mimeType)
        return if (extension.isNullOrBlank()) base else "$base.$extension"
    }

    private fun newId(): String =
        System.currentTimeMillis().toString(36) + "-" + random.nextInt(Int.MAX_VALUE).toString(36)

    private fun deletePayload(context: Context, item: SharedItem) {
        item.path?.let { runCatching { File(it).delete() } }
    }

    private fun streamUriOf(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun streamUrisOf(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun readIndex(context: Context): List<SharedItem> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { position ->
                val json = array.optJSONObject(position) ?: return@mapNotNull null
                val path = json.optString("path").takeIf { it.isNotEmpty() }
                SharedItem(
                    id = json.optString("id"),
                    kind = json.optString("kind", ShareReceiveContract.KIND_FILE),
                    mimeType = json.optString("mimeType", "application/octet-stream"),
                    name = json.optString("name"),
                    size = json.optLong("size"),
                    path = path,
                    text = json.optString("text").takeIf { it.isNotEmpty() },
                    receivedAt = json.optLong("receivedAt", System.currentTimeMillis()),
                    consumedAt = json.optLong("consumedAt")
                )
            }.filter { it.id.isNotEmpty() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Corrupt inbox index, resetting: ${e.message}")
            runCatching { file.delete() }
            emptyList()
        }
    }

    private fun writeIndex(context: Context, items: List<SharedItem>) {
        try {
            val dir = inboxDir(context).apply { mkdirs() }
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("kind", item.kind)
                        put("mimeType", item.mimeType)
                        put("name", item.name)
                        put("size", item.size)
                        item.path?.let { put("path", it) }
                        item.text?.let { put("text", it) }
                        put("receivedAt", item.receivedAt)
                        put("consumedAt", item.consumedAt)
                    }
                )
            }
            File(dir, ShareReceiveContract.INDEX_FILE_NAME).writeText(array.toString())
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to write inbox index: ${e.message}")
        }
    }
}
