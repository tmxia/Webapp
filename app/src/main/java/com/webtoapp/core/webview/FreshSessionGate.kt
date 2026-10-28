package com.webtoapp.core.webview

import android.os.Handler
import android.os.Looper
import java.util.Collections











internal object FreshSessionGate {

    const val TIMEOUT_MS = 5_000L

    private val tickets = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    private val waiters = ArrayList<() -> Unit>()

    fun open(armTimeout: Boolean = true): Any {
        val ticket = Any()
        synchronized(this) { tickets.add(ticket) }
        if (armTimeout) armTimeout(ticket)
        return ticket
    }

    fun close(ticket: Any) {
        val ready = synchronized(this) {
            val removed = tickets.remove(ticket)
            if (!removed || tickets.isNotEmpty()) {
                emptyList()
            } else {
                val copy = waiters.toList()
                waiters.clear()
                copy
            }
        }
        ready.forEach { waiter ->
            try {
                waiter()
            } catch (t: Throwable) {
                android.util.Log.w("FreshSessionGate", "Fresh-session navigation failed", t)
            }
        }
    }

    fun runWhenReady(block: () -> Unit) {
        val runNow = synchronized(this) {
            if (tickets.isEmpty()) {
                true
            } else {
                waiters.add(block)
                false
            }
        }
        if (runNow) block()
    }

    internal fun resetForTest() {
        synchronized(this) {
            tickets.clear()
            waiters.clear()
        }
    }

    internal fun pendingForTest(): Int = synchronized(this) { tickets.size }

    private fun armTimeout(ticket: Any) {
        try {
            val looper = Looper.getMainLooper() ?: return
            Handler(looper).postDelayed({ close(ticket) }, TIMEOUT_MS)
        } catch (_: Throwable) {
            
            
        }
    }
}
