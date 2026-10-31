package com.webtoapp.core.webview

import android.content.Context
import android.net.Uri
















class WebViewResumeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sessionKey(appId: Long, directUrl: String?, previewBaseUrl: String?, isTest: Boolean): String? = when {
        isTest -> null
        appId > 0 -> "app:$appId"
        !directUrl.isNullOrBlank() -> "url:$directUrl"
        !previewBaseUrl.isNullOrBlank() -> "preview:$previewBaseUrl"
        else -> null
    }

    fun persist(key: String?, baseUrl: String?, lastUrl: String?) {
        if (key == null || baseUrl.isNullOrBlank()) return
        if (lastUrl.isNullOrBlank() || !isResumable(lastUrl)) return
        
        
        if (lastUrl == prefs.getString(key + SUFFIX_JUMP, null)) return
        prefs.edit()
            .putString(key + SUFFIX_BASE, baseUrl)
            .putString(key + SUFFIX_LAST, lastUrl)
            .apply()
    }

    fun resumeUrl(key: String?, baseUrl: String?): String? {
        if (key == null || baseUrl.isNullOrBlank()) return null
        if (prefs.getString(key + SUFFIX_BASE, null) != baseUrl) return null
        val last = prefs.getString(key + SUFFIX_LAST, null)
        
        if (last != null && last == prefs.getString(key + SUFFIX_JUMP, null)) return null
        return last
    }

    






    fun persistExternalJump(key: String?, baseUrl: String?, url: String?) {
        if (key == null || baseUrl.isNullOrBlank() || url.isNullOrBlank()) return
        prefs.edit()
            .putString(key + SUFFIX_BASE, baseUrl)
            .putString(key + SUFFIX_JUMP, url)
            .apply()
    }

    fun consumeExternalJump(key: String?, baseUrl: String?): String? {
        if (key == null || baseUrl.isNullOrBlank()) return null
        if (prefs.getString(key + SUFFIX_BASE, null) != baseUrl) return null
        val url = prefs.getString(key + SUFFIX_JUMP, null)
        if (url != null) prefs.edit().remove(key + SUFFIX_JUMP).apply()
        return url
    }

    fun clear(key: String?) {
        if (key == null) return
        prefs.edit().remove(key + SUFFIX_BASE).remove(key + SUFFIX_LAST).remove(key + SUFFIX_JUMP).apply()
    }

    private fun isResumable(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host !in LOCAL_HOSTS
    }

    private companion object {
        const val PREFS_NAME = "webview_preview_resume"
        const val SUFFIX_BASE = ".base"
        const val SUFFIX_LAST = ".last"
        const val SUFFIX_JUMP = ".jump"
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
    }
}
