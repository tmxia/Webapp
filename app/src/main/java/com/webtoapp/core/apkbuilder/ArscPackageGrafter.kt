package com.webtoapp.core.apkbuilder

import com.webtoapp.core.logging.AppLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder

















class ArscPackageGrafter {

    data class Result(
        
        val arsc: ByteArray,
        
        val resPaths: Map<String, String>
    )

    




    fun graft(baseArsc: ByteArray, stackArsc: ByteArray, resPathPrefix: String): Result {
        require(resPathPrefix.endsWith("/")) { "resPathPrefix must end with '/'" }

        val base = ArscTable.parse(baseArsc)
        val stack = ArscTable.parse(stackArsc)
        val stackPkg = stack.packages.firstOrNull()
            ?: throw IllegalArgumentException("stack arsc has no package chunk")
        if (base.packages.any { it.id == stackPkg.id }) {
            throw IllegalArgumentException(
                "package id 0x${stackPkg.id.toString(16)} already present in base arsc"
            )
        }
        require(stackPkg.id >= 0x80) {
            "stack package id 0x${stackPkg.id.toString(16)} must be >= 0x80"
        }

        val baseStringCount = base.globalStrings.size

        
        
        val refs = mutableListOf<Pair<Int, Int>>() 
        val resPaths = linkedMapOf<String, String>()
        stackPkg.forEachStringValue { chunkOffset, poolIndex ->
            val value = stack.globalStrings[poolIndex]
            val rewritten = if (value.startsWith("res/")) {
                val target = resPathPrefix + value.removePrefix("res/")
                resPaths[value] = target
                target
            } else {
                value
            }
            refs += chunkOffset to base.indexOfOrAppend(rewritten)
        }

        
        val newPool = buildUtf8StringPool(base.globalStrings)

        
        val chunk = stackPkg.raw.copyOf()
        val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        for ((chunkOffset, newIndex) in refs) {
            buf.putInt(chunkOffset, newIndex)
        }

        
        val head = baseArsc.copyOfRange(0, base.headerSize)
        val body = baseArsc.copyOfRange(
            base.headerSize + base.globalPoolSize,
            baseArsc.size
        )
        val out = ByteBuffer.allocate(head.size + newPool.size + body.size + chunk.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(head).put(newPool).put(body).put(chunk)
            .array()
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, out.size)              
            putInt(8, base.packageCount + 1) 
        }

        AppLogger.d(
            TAG,
            "grafted stack package 0x${stackPkg.id.toString(16)} " +
                "(${stackPkg.name}): ${base.globalStrings.size - baseStringCount} new strings, " +
                "${resPaths.size} res paths"
        )
        return Result(out, resPaths)
    }

    

    internal class ArscTable(
        val headerSize: Int,
        val packageCount: Int,
        val globalStrings: MutableList<String>,
        val globalPoolSize: Int,
        val packages: List<PackageChunk>
    ) {
        fun indexOfOrAppend(s: String): Int {
            val idx = globalStrings.indexOf(s)
            if (idx >= 0) return idx
            globalStrings.add(s)
            return globalStrings.size - 1
        }

        class PackageChunk(val id: Int, val name: String, val raw: ByteArray) {
            




            fun forEachStringValue(visit: (chunkOffset: Int, poolIndex: Int) -> Unit) {
                val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                var off = buf.getShort(2).toInt() and 0xFFFF 
                while (off + 8 <= raw.size) {
                    val type = buf.getShort(off).toInt() and 0xFFFF
                    val size = buf.getInt(off + 4)
                    if (size <= 0 || off + size > raw.size) break
                    if (type == RES_TABLE_TYPE) {
                        walkTypeChunk(buf, off, size, visit)
                    }
                    off += size
                }
            }

            private fun walkTypeChunk(
                buf: ByteBuffer,
                off: Int,
                size: Int,
                visit: (Int, Int) -> Unit
            ) {
                val headerSize = buf.getShort(off + 2).toInt() and 0xFFFF
                val entryCount = buf.getInt(off + 12)
                val entriesStart = buf.getInt(off + 16)
                val offsetsBase = off + headerSize
                for (e in 0 until entryCount) {
                    val rel = buf.getInt(offsetsBase + 4 * e)
                    if (rel == NO_ENTRY) continue
                    val eo = off + entriesStart + rel
                    if (eo + 8 > off + size) break
                    val flags = buf.getShort(eo + 2).toInt() and 0xFFFF
                    if (flags and 0x0001 != 0) {
                        
                        
                        val count = buf.getInt(eo + 12)
                        var mo = eo + 16
                        repeat(count) {
                            visitStringValue(buf, mo + 4, visit)
                            mo += 12
                        }
                    } else {
                        visitStringValue(buf, eo + 8, visit)
                    }
                }
            }

            private fun visitStringValue(buf: ByteBuffer, vo: Int, visit: (Int, Int) -> Unit) {
                if (vo + 8 > buf.capacity()) return
                val dataType = buf.get(vo + 3).toInt() and 0xFF
                if (dataType != TYPE_STRING) return
                visit(vo + 4, buf.getInt(vo + 4))
            }
        }

        companion object {
            fun parse(data: ByteArray): ArscTable {
                val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                require(buf.getShort(0).toInt() and 0xFFFF == RES_TABLE) { "not a resources.arsc" }
                val headerSize = buf.getShort(2).toInt() and 0xFFFF
                val packageCount = buf.getInt(8)
                val pool = StringPool.read(buf, headerSize)
                val packages = mutableListOf<PackageChunk>()
                var off = headerSize + pool.chunkSize
                while (off + 8 <= data.size) {
                    val type = buf.getShort(off).toInt() and 0xFFFF
                    val size = buf.getInt(off + 4)
                    if (size <= 0 || off + size > data.size) break
                    if (type == RES_TABLE_PACKAGE) {
                        packages += PackageChunk(
                            id = buf.getInt(off + 8),
                            name = readPackageName(buf, off),
                            raw = data.copyOfRange(off, off + size)
                        )
                    }
                    off += size
                }
                return ArscTable(headerSize, packageCount, pool.strings, pool.chunkSize, packages)
            }

            private fun readPackageName(buf: ByteBuffer, off: Int): String {
                val sb = StringBuilder()
                for (i in 0 until 128) {
                    val c = buf.getShort(off + 12 + i * 2).toInt()
                    if (c == 0) break
                    sb.append(c.toChar())
                }
                return sb.toString()
            }
        }
    }

    internal class StringPool(
        val strings: MutableList<String>,
        val chunkSize: Int
    ) {
        companion object {
            fun read(buf: ByteBuffer, off: Int): StringPool {
                val size = buf.getInt(off + 4)
                val stringCount = buf.getInt(off + 8)
                val flags = buf.getInt(off + 16)
                val stringsStart = buf.getInt(off + 20)
                val isUtf8 = flags and UTF8_FLAG != 0
                val dataBase = off + stringsStart
                val offsetsBase = off + (buf.getShort(off + 2).toInt() and 0xFFFF)
                val strings = MutableList(stringCount) { i ->
                    val sOff = dataBase + buf.getInt(offsetsBase + 4 * i)
                    decodeLengthPrefixed(buf, sOff, isUtf8)
                }
                return StringPool(strings, size)
            }

            private fun decodeLengthPrefixed(buf: ByteBuffer, off: Int, utf8: Boolean): String {
                if (utf8) {
                    var o = off + utf8LenLen(buf, off) 
                    val n = utf8LenAt(buf, o).also { o += utf8LenSize(buf, o) }
                    val bytes = ByteArray(n)
                    buf.get(o, bytes)
                    return String(bytes, Charsets.UTF_8)
                }
                val n = utf16LenAt(buf, off)
                val o = off + utf16LenSize(buf, off)
                val chars = CharArray(n) { buf.getShort(o + it * 2).toInt().toChar() }
                return String(chars)
            }

            private fun utf16LenAt(buf: ByteBuffer, off: Int): Int {
                val v = buf.getShort(off).toInt() and 0xFFFF
                return if (v and 0x8000 != 0)
                    (v and 0x7FFF shl 16) or (buf.getShort(off + 2).toInt() and 0xFFFF)
                else v
            }

            private fun utf16LenSize(buf: ByteBuffer, off: Int): Int =
                if (buf.getShort(off).toInt() and 0x8000 != 0) 4 else 2

            private fun utf8LenAt(buf: ByteBuffer, off: Int): Int {
                val v = buf.get(off).toInt() and 0xFF
                return if (v and 0x80 != 0)
                    (v and 0x7F shl 8) or (buf.get(off + 1).toInt() and 0xFF)
                else v
            }

            private fun utf8LenSize(buf: ByteBuffer, off: Int): Int =
                if (buf.get(off).toInt() and 0x80 != 0) 2 else 1

            private fun utf8LenLen(buf: ByteBuffer, off: Int): Int = utf8LenSize(buf, off)
        }
    }

    companion object {
        private const val TAG = "ArscPackageGrafter"
        private const val RES_STRING_POOL: Short = 0x0001
        private const val RES_TABLE = 0x0002
        private const val RES_TABLE_PACKAGE = 0x0200
        private const val RES_TABLE_TYPE = 0x0201
        private const val TYPE_STRING = 0x03
        private const val NO_ENTRY = -1
        private const val UTF8_FLAG = 0x00000100

        private fun buildUtf8StringPool(strings: List<String>): ByteArray {
            val headerSize = 28
            val count = strings.size
            val offsetsPos = headerSize + 4 * count
            val blob = java.io.ByteArrayOutputStream()
            val offsets = IntArray(count)
            strings.forEachIndexed { i, s ->
                offsets[i] = blob.size()
                val utf8 = s.toByteArray(Charsets.UTF_8)
                val utf16Len = s.length 
                blob.write(encLen8(utf16Len))
                blob.write(encLen8(utf8.size))
                blob.write(utf8)
                blob.write(0)
            }
            while (blob.size() % 4 != 0) blob.write(0)
            val total = offsetsPos + blob.size()
            val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
            out.putShort(RES_STRING_POOL)
            out.putShort(headerSize.toShort())
            out.putInt(total)
            out.putInt(count)
            out.putInt(0)             
            out.putInt(UTF8_FLAG)
            out.putInt(offsetsPos)    
            out.putInt(0)             
            offsets.forEach { out.putInt(it) }
            out.put(blob.toByteArray())
            return out.array()
        }

        private fun encLen8(n: Int): ByteArray =
            if (n >= 0x80) byteArrayOf((0x80 or (n shr 8)).toByte(), (n and 0xFF).toByte())
            else byteArrayOf(n.toByte())
    }
}
