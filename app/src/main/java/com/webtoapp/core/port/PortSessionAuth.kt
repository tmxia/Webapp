package com.webtoapp.core.port

import java.security.SecureRandom

















object PortSessionAuth {

    private const val TTL_MS = 60_000L
    private const val MAX_TOKENS = 32

    private val random = SecureRandom()
    private val lock = Any()

    
    private val issued = LinkedHashMap<String, Long>()

    




    fun issue(now: Long = System.currentTimeMillis()): String {
        synchronized(lock) {
            purgeExpiredLocked(now)
            val token = ByteArray(24).also { random.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            issued[token] = now
            while (issued.size > MAX_TOKENS) {
                issued.remove(issued.entries.first().key)
            }
            return token
        }
    }

    



    fun consume(token: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (token.isNullOrBlank()) return false
        synchronized(lock) {
            purgeExpiredLocked(now)
            return issued.remove(token) != null
        }
    }

    
    fun clear() {
        synchronized(lock) { issued.clear() }
    }

    private fun purgeExpiredLocked(now: Long) {
        val it = issued.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > TTL_MS) it.remove()
        }
    }
}
