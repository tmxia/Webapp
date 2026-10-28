package com.webtoapp.core.webview

import java.net.URLEncoder











internal object TranslateTextPlan {

    data class Limit(val maxRawChars: Int, val maxEncodedChars: Int?)

    val GOOGLE = Limit(maxRawChars = 1200, maxEncodedChars = 1800)
    val MYMEMORY = Limit(maxRawChars = 400, maxEncodedChars = 450)
    val LIBRE = Limit(maxRawChars = 1800, maxEncodedChars = null)
    val LINGVA = Limit(maxRawChars = 800, maxEncodedChars = 1500)

    data class Piece(
        val textIndex: Int,
        val separatorBefore: String,
        val text: String,
        
        val passthrough: Boolean
    )

    fun piecesFor(texts: List<String>, limit: Limit): List<Piece> {
        val pieces = mutableListOf<Piece>()
        texts.forEachIndexed { textIndex, text ->
            val lines = text.split('\n')
            lines.forEachIndexed { lineIndex, line ->
                val segments = splitLine(line, limit)
                segments.forEachIndexed { segmentIndex, segment ->
                    val separator = when {
                        lineIndex == 0 && segmentIndex == 0 -> ""
                        segmentIndex == 0 -> "\n"
                        else -> ""
                    }
                    pieces += Piece(
                        textIndex = textIndex,
                        separatorBefore = separator,
                        text = segment,
                        passthrough = segment.isEmpty()
                    )
                }
            }
        }
        return pieces
    }

    fun batches(pieces: List<Piece>, limit: Limit): List<List<Int>> {
        val batches = mutableListOf<List<Int>>()
        val current = mutableListOf<Int>()
        var joined = ""
        pieces.forEachIndexed { index, piece ->
            if (piece.passthrough) return@forEachIndexed
            val candidate = if (current.isEmpty()) piece.text else "$joined\n${piece.text}"
            if (current.isNotEmpty() && !within(candidate, limit)) {
                batches += current.toList()
                current.clear()
                joined = piece.text
                current += index
            } else {
                current += index
                joined = candidate
            }
        }
        if (current.isNotEmpty()) batches += current.toList()
        return batches
    }

    fun join(pieces: List<Piece>, indices: List<Int>): String {
        return indices.joinToString("\n") { pieces[it].text }
    }

    




    fun splitTranslated(translated: String, count: Int): List<String>? {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(translated)
        val parts = translated.split('\n')
        if (parts.size != count) return null
        return parts
    }

    fun reassemble(textCount: Int, pieces: List<Piece>, translated: List<String>): List<String> {
        val builders = Array(textCount) { StringBuilder() }
        pieces.forEachIndexed { index, piece ->
            builders[piece.textIndex]
                .append(piece.separatorBefore)
                .append(translated[index])
        }
        return builders.map { it.toString() }
    }

    fun within(text: String, limit: Limit): Boolean {
        if (text.length > limit.maxRawChars) return false
        val encodedBudget = limit.maxEncodedChars ?: return true
        return encodedLength(text) <= encodedBudget
    }

    internal fun encodedLength(text: String): Int {
        return URLEncoder.encode(text, Charsets.UTF_8.name()).length
    }

    private fun splitLine(line: String, limit: Limit): List<String> {
        if (line.isEmpty() || within(line, limit)) return listOf(line)
        val out = mutableListOf<String>()
        var rest = line
        while (rest.isNotEmpty() && !within(rest, limit)) {
            val prefixEnd = longestPrefixWithin(rest, limit).coerceAtLeast(1)
            val window = rest.substring(0, prefixEnd)
            val breakAt = lastBreak(window)
            val cut = if (breakAt >= prefixEnd / 3 && within(rest.substring(0, breakAt), limit)) {
                breakAt
            } else {
                prefixEnd
            }
            val piece = rest.substring(0, cut).trimEnd()
            if (piece.isEmpty()) {
                out += rest.substring(0, 1)
                rest = rest.substring(1)
            } else {
                out += piece
                rest = rest.substring(cut).trimStart()
            }
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private fun longestPrefixWithin(text: String, limit: Limit): Int {
        var low = 1
        var high = text.length
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (within(text.substring(0, mid), limit)) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun lastBreak(window: String): Int {
        val marks = arrayOf(". ", "! ", "? ", "。", "！", "？", "; ", "；", "，", ", ", " ")
        var best = -1
        for (mark in marks) {
            val at = window.lastIndexOf(mark)
            if (at >= 0) {
                val end = at + mark.length
                if (end > best) best = end
            }
        }
        return best
    }
}
