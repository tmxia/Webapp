package com.webtoapp.core.webview

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.WebView
import com.webtoapp.core.engine.BrowserSurface
import com.webtoapp.core.engine.GeckoViewEngine














object PageKeyEvents {

    const val PROMPT_TOKEN = "__wta_key__"

    private val mainHandler: Handler? by lazy {
        Looper.getMainLooper()?.let { Handler(it) }
    }

    private var geckoWaiter: ((Boolean) -> Unit)? = null

    private var geckoGeneration = 0

    fun volumeKeyName(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> "VolumeUp"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "VolumeDown"
        KeyEvent.KEYCODE_VOLUME_MUTE -> "VolumeMute"
        else -> null
    }

    



    fun handleVolume(
        event: KeyEvent,
        forward: Boolean,
        consume: Boolean,
        webView: WebView?,
        surface: BrowserSurface?
    ): Boolean {
        if (!forward || event.action != KeyEvent.ACTION_DOWN) return false
        val name = volumeKeyName(event.keyCode) ?: return false
        dispatchVolume(name, webView, surface)
        return consume
    }

    fun dispatchBack(
        webView: WebView?,
        surface: BrowserSurface?,
        onPrevented: (Boolean) -> Unit
    ) {
        if (webView != null) {
            webView.evaluateJavascript(backScript(reportViaPrompt = false)) { result ->
                onPrevented(result == "true")
            }
            return
        }
        val engine = surface?.engine
        if (engine is GeckoViewEngine) {
            awaitGecko(onPrevented)
            engine.evaluateJavascript(backScript(reportViaPrompt = true), null)
            return
        }
        onPrevented(false)
    }

    



    fun claimGeckoPrompt(message: String?, value: String?): Boolean {
        if (message != PROMPT_TOKEN) return false
        reportGecko(value == "1")
        return true
    }

    fun backScript(reportViaPrompt: Boolean): String {
        val report = if (reportViaPrompt) {
            "try { prompt('$PROMPT_TOKEN', prevented ? '1' : '0'); } catch (e) {}"
        } else {
            ""
        }
        return """
            (function() {
                var prevented = false;
                try {
                    var evt = new CustomEvent('wta:back', { bubbles: true, cancelable: true });
                    prevented = !document.dispatchEvent(evt);
                } catch (e) {}
                $report
                return prevented;
            })();
        """.trimIndent()
    }

    private fun dispatchVolume(name: String, webView: WebView?, surface: BrowserSurface?) {
        val script = """
            (function() {
                try {
                    document.dispatchEvent(new CustomEvent('wta:volume', {
                        bubbles: true,
                        cancelable: true,
                        detail: { key: '$name' }
                    }));
                } catch (e) {}
            })();
        """.trimIndent()
        when {
            webView != null -> webView.evaluateJavascript(script, null)
            surface != null -> surface.evaluateJavascript(script, null)
        }
    }

    private fun awaitGecko(onPrevented: (Boolean) -> Unit) {
        geckoWaiter?.invoke(false)
        val token = ++geckoGeneration
        geckoWaiter = onPrevented
        val handler = mainHandler
        if (handler == null) {
            reportGecko(false)
            return
        }
        handler.postDelayed({
            if (token == geckoGeneration) reportGecko(false)
        }, 500L)
    }

    private fun reportGecko(prevented: Boolean) {
        val waiter = geckoWaiter ?: return
        geckoWaiter = null
        geckoGeneration++
        waiter(prevented)
    }
}
