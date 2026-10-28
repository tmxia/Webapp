package com.webtoapp.core.webview












internal class MainFrameHttpRetry(
    private val maxDistinctUrls: Int = 8
) {
    private val retried = LinkedHashSet<String>()

    fun shouldSilentRetry(url: String?, statusCode: Int): Boolean {
        if (statusCode != 403) return false
        if (url.isNullOrBlank() || url == "about:blank") return false
        if (url.startsWith("data:") || url.startsWith("file:")) return false
        if (url in retried) return false
        if (retried.size >= maxDistinctUrls) return false
        retried.add(url)
        return true
    }
}






internal fun mainFrameHttpRetryHeaders(failedUrl: String, referer: String?): Map<String, String> {
    if (referer.isNullOrBlank() || referer == failedUrl || referer == "about:blank") return emptyMap()
    if (!referer.startsWith("http://") && !referer.startsWith("https://")) return emptyMap()
    return mapOf("Referer" to referer)
}
