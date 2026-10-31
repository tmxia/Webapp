package com.webtoapp.core.webview
















internal class ReloadCircuitBreaker(
    private val maxReloads: Int = 6,
    private val windowMs: Long = 10_000L,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val hits = ArrayDeque<Pair<String, Long>>()

    




    fun noteAutoReload(url: String?): Boolean {
        val host = hostOf(url) ?: return true
        val now = clockMs()
        synchronized(lock) {
            while (hits.isNotEmpty() && now - hits.first().second > windowMs) {
                hits.removeFirst()
            }
            if (hits.count { it.first == host } >= maxReloads) return false
            hits.addLast(host to now)
            return true
        }
    }

    internal fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            java.net.URI(url).host?.lowercase()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
