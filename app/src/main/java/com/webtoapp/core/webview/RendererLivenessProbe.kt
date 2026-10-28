package com.webtoapp.core.webview

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

















object RendererLivenessProbe {

    const val DEFAULT_TIMEOUT_MS = 2000L

    fun probe(
        webView: WebView?,
        stillCurrent: () -> Boolean,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        handler: Handler = Handler(Looper.getMainLooper()),
        onDead: () -> Unit
    ) {
        val wv = webView ?: return

        var answered = false
        try {
            
            
            
            val url = wv.url
            
            if (url.isNullOrBlank() || url == "about:blank") return

            wv.evaluateJavascript("void 0") { answered = true }
        } catch (e: Exception) {
            
            if (stillCurrent()) onDead()
            return
        }

        handler.postDelayed({
            if (!answered && stillCurrent()) onDead()
        }, timeoutMs)
    }
}
