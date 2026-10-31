package com.webtoapp.core.share

import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import com.webtoapp.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
















class ShareInboxBridge(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    




    @JavascriptInterface
    fun consume(idsJson: String?) {
        val ids = try {
            val array = JSONArray(idsJson ?: return)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (ids.isEmpty()) return

        scope.launch {
            try {
                SharedContentInbox.consume(context, ids)
            } catch (e: Exception) {
                AppLogger.w("ShareInboxBridge", "Failed to consume share items: ${e.message}")
            }
        }
    }

    



    @JavascriptInterface
    fun payloadSize(id: String?): Long {
        return try {
            val item = SharedContentInbox.lookup(context, id ?: return -1L) ?: return -1L
            if (item.isText) return -1L
            val file = item.path?.let(::File) ?: return -1L
            if (!file.isFile) return -1L
            file.length()
        } catch (e: Exception) {
            AppLogger.w("ShareInboxBridge", "payloadSize failed: ${e.message}")
            -1L
        }
    }

    
    @JavascriptInterface
    fun payloadMime(id: String?): String {
        return try {
            val item = SharedContentInbox.lookup(context, id ?: return "") ?: return ""
            item.mimeType
        } catch (e: Exception) {
            ""
        }
    }

    




    @JavascriptInterface
    fun readChunk(id: String?, offset: Int, length: Int): String {
        return try {
            val bytes = SharedContentInbox.readChunk(
                context,
                id ?: return "",
                offset,
                length
            ) ?: return ""
            if (bytes.isEmpty()) return ""
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.w("ShareInboxBridge", "readChunk failed: ${e.message}")
            ""
        }
    }
}
