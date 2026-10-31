package com.webtoapp.core.webview

















object WebViewRestoreGuard {

    fun isUsableRestoredUrl(url: String?, externalJumpUrl: String?): Boolean {
        if (url.isNullOrBlank() || url == "about:blank") return false
        val isWebContent = url.startsWith("http://") ||
            url.startsWith("https://") ||
            url.startsWith("file://")
        if (!isWebContent) return false
        return url != externalJumpUrl
    }
}
