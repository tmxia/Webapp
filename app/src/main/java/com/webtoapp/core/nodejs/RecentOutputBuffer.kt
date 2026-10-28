package com.webtoapp.core.nodejs









class RecentOutputBuffer(
    private val maxLines: Int = 80,
    private val maxChars: Int = 6000
) {

    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }

    @Synchronized
    fun contains(substring: String): Boolean = lines.any { it.contains(substring) }

    




    @Synchronized
    fun snapshot(): String {
        if (lines.isEmpty()) return ""
        var text = lines.joinToString("\n")
        if (text.length > maxChars) {
            val cut = text.length - maxChars
            val newline = text.indexOf('\n', cut)
            text = "…" + text.substring(if (newline > 0) newline + 1 else cut)
        }
        return text
    }
}
