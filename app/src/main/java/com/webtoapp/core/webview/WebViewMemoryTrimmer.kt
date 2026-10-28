package com.webtoapp.core.webview

import android.content.ComponentCallbacks2
import android.content.Context
import android.webkit.CookieManager
import com.webtoapp.core.logging.AppLogger




















object WebViewMemoryTrimmer {

    private const val TAG = "WebViewMemoryTrimmer"

    enum class TrimAction { NONE, LIGHT, HEAVY, TEARDOWN }

    
    fun actionFor(level: Int): TrimAction = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> TrimAction.TEARDOWN
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> TrimAction.HEAVY
        level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> TrimAction.HEAVY
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> TrimAction.LIGHT
        else -> TrimAction.NONE
    }

    
    fun shouldTeardownWebView(level: Int): Boolean =
        actionFor(level) == TrimAction.TEARDOWN

    



    fun onTrimMemory(level: Int, context: Context): Boolean {
        return when (actionFor(level)) {
            TrimAction.NONE -> false
            TrimAction.LIGHT -> {
                trimImageCache(context, level)
                false
            }
            TrimAction.HEAVY -> {
                flushCookies()
                clearImageCache(context)
                false
            }
            TrimAction.TEARDOWN -> {
                flushCookies()
                clearImageCache(context)
                runCatching { WebViewPool.release() }
                true
            }
        }
    }

    private fun trimImageCache(context: Context, level: Int) {
        runCatching {
            coil.Coil.imageLoader(context.applicationContext).memoryCache?.trimMemory(level)
        }.onFailure {
            AppLogger.w(TAG, "Image cache trim failed: ${it.message}")
        }
    }

    private fun clearImageCache(context: Context) {
        runCatching {
            coil.Coil.imageLoader(context.applicationContext).memoryCache?.clear()
        }.onFailure {
            AppLogger.w(TAG, "Image cache clear failed: ${it.message}")
        }
    }

    private fun flushCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }
}
