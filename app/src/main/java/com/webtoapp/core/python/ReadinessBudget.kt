package com.webtoapp.core.python

import java.util.concurrent.atomic.AtomicLong















internal class ReadinessBudget(
    private val baseMs: Long,
    private val hardCapMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val startAt = AtomicLong(clock())
    private val lastProgressAt = AtomicLong(clock())

    
    fun markProgress() {
        lastProgressAt.set(clock())
    }

    
    fun expired(): Boolean {
        val now = clock()
        return now - startAt.get() >= hardCapMs || now - lastProgressAt.get() >= baseMs
    }

    fun elapsedMs(): Long = clock() - startAt.get()

    fun msSinceProgress(): Long = clock() - lastProgressAt.get()
}
