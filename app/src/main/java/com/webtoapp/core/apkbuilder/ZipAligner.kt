package com.webtoapp.core.apkbuilder

import com.webtoapp.core.logging.AppLogger
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipAligner {

    private const val TAG = "ZipAligner"

    private const val LFH_FIXED_SIZE = 30

    private const val DEFAULT_ALIGNMENT = 4L
    private const val NATIVE_LIB_ALIGNMENT = 16 * 1024L

    private const val COPY_BUFFER_SIZE = 64 * 1024

    private fun computeCrc(zipIn: ZipFile, entry: ZipEntry): Long {
        val crc = CRC32()
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        zipIn.getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    fun alignInPlace(apkFile: File): Boolean {
        
        
        try {
            if (verifyNativeLibAlignment(apkFile)) {
                AppLogger.d(TAG, "APK already 16KB-aligned, skipping rewrite")
                return true
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Pre-align check failed, proceeding with full align: ${e.message}")
        }
        val tempFile = File(apkFile.parent, apkFile.name + ".aligned")
        return try {
            val result = align(apkFile, tempFile)
            if (result && isValidZip(tempFile)) {
                apkFile.delete()
                tempFile.renameTo(apkFile)
                true
            } else {
                if (result) {
                    AppLogger.e(TAG, "Aligned APK is not a valid zip; keeping original unaligned APK")
                }
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "ZipAlign failed", e)
            tempFile.delete()
            false
        }
    }

    private fun isValidZip(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            ZipFile(file).use { zip ->
                zip.entries().hasMoreElements()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Aligned APK validation failed: ${e.message}")
            false
        }
    }


    







    private class ManualZipWriter(out: OutputStream) : Closeable {
        private val stream = java.io.BufferedOutputStream(out, COPY_BUFFER_SIZE)
        private val entries = mutableListOf<CentralDirRecord>()
        private var offset = 0L

        private class CentralDirRecord(
            val name: ByteArray,
            val crc: Long,
            val size: Long,
            val compressedSize: Long,
            val method: Int,
            val extra: ByteArray?,
            val lfhOffset: Long
        )

        private fun write(buf: ByteArray, from: Int = 0, length: Int = buf.size) {
            stream.write(buf, from, length)
            offset += length
        }

        private fun localHeader(
            name: ByteArray,
            crc: Long,
            size: Long,
            compressedSize: Long,
            method: Int,
            extra: ByteArray?
        ) {
            val hdr = ByteBuffer.allocate(LFH_FIXED_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            hdr.putInt(0x04034b50)
            hdr.putShort(20)                        
            hdr.putShort(0)                         
            hdr.putShort(method.toShort())
            hdr.putShort(0)                         
            hdr.putShort(0)                         
            hdr.putInt(crc.toInt())
            hdr.putInt(compressedSize.toInt())
            hdr.putInt(size.toInt())
            hdr.putShort(name.size.toShort())
            hdr.putShort((extra?.size ?: 0).toShort())
            write(hdr.array())
            write(name)
            if (extra != null && extra.isNotEmpty()) write(extra)
        }

        
        fun addStored(name: String, data: InputStream, size: Long, crc: Long, extra: ByteArray?) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val lfhOffset = offset
            localHeader(nameBytes, crc, size, size, ZipEntry.STORED, extra)
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val read = data.read(buffer)
                if (read < 0) break
                write(buffer, 0, read)
            }
            entries += CentralDirRecord(nameBytes, crc, size, size, ZipEntry.STORED, extra, lfhOffset)
        }

        
        fun addDeflated(name: String, data: InputStream) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val lfhOffset = offset
            localHeader(nameBytes, 0, 0, 0, ZipEntry.DEFLATED, null)
            val deflater = java.util.zip.Deflater(
                java.util.zip.Deflater.DEFAULT_COMPRESSION, true
            )
            val crc = CRC32()
            var raw = 0L
            var compressed = 0L
            val input = ByteArray(COPY_BUFFER_SIZE)
            val buf = ByteArray(COPY_BUFFER_SIZE)
            try {
                while (true) {
                    val read = data.read(input)
                    if (read < 0) break
                    crc.update(input, 0, read)
                    raw += read
                    deflater.setInput(input, 0, read)
                    while (!deflater.needsInput()) {
                        val n = deflater.deflate(buf)
                        if (n == 0) break
                        write(buf, 0, n)
                        compressed += n
                    }
                }
                deflater.finish()
                while (!deflater.finished()) {
                    val n = deflater.deflate(buf)
                    if (n == 0) break
                    write(buf, 0, n)
                    compressed += n
                }
            } finally {
                deflater.end()
            }
            
            
            
            val patch = ByteBuffer.allocate(LFH_FIXED_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            patch.putInt(0x04034b50)
            patch.putShort(20)
            patch.putShort(0)
            patch.putShort(ZipEntry.DEFLATED.toShort())
            patch.putShort(0)
            patch.putShort(0)
            patch.putInt(crc.value.toInt())
            patch.putInt(compressed.toInt())
            patch.putInt(raw.toInt())
            patch.putShort(nameBytes.size.toShort())
            patch.putShort(0)
            entries += CentralDirRecord(nameBytes, crc.value, raw, compressed, ZipEntry.DEFLATED, null, lfhOffset)
            pendingHeaderPatches += lfhOffset to patch.array()
        }

        private val pendingHeaderPatches = mutableListOf<Pair<Long, ByteArray>>()
        val offsetOfEntry: Long get() = offset

        
        fun addDirectory(name: String) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val lfhOffset = offset
            localHeader(nameBytes, 0, 0, 0, ZipEntry.STORED, null)
            entries += CentralDirRecord(nameBytes, 0, 0, 0, ZipEntry.STORED, null, lfhOffset)
        }

        private fun centralDirectoryEntry(e: CentralDirRecord): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val hdr = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
            hdr.putInt(0x02014b50)
            hdr.putShort(20)                        
            hdr.putShort(20)                        
            hdr.putShort(0)                         
            hdr.putShort(e.method.toShort())
            hdr.putShort(0)                         
            hdr.putShort(0)                         
            hdr.putInt(e.crc.toInt())
            hdr.putInt(e.compressedSize.toInt())
            hdr.putInt(e.size.toInt())
            hdr.putShort(e.name.size.toShort())
            hdr.putShort((e.extra?.size ?: 0).toShort())
            hdr.putShort(0)                         
            hdr.putShort(0)                         
            hdr.putShort(0)                         
            hdr.putInt(0)                           
            hdr.putInt(e.lfhOffset.toInt())
            out.write(hdr.array())
            out.write(e.name)
            if (e.extra != null) out.write(e.extra)
            return out.toByteArray()
        }

        override fun close() {
            val cdStart = offset
            val cdBytes = java.io.ByteArrayOutputStream()
            entries.forEach { cdBytes.write(centralDirectoryEntry(it)) }
            val cd = cdBytes.toByteArray()
            write(cd)
            val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            eocd.putInt(0x06054b50)
            eocd.putShort(0)
            eocd.putShort(0)
            eocd.putShort(entries.size.toShort())
            eocd.putShort(entries.size.toShort())
            eocd.putInt(cd.size)
            eocd.putInt(cdStart.toInt())
            eocd.putShort(0)
            write(eocd.array())
            stream.flush()
            stream.close()
        }

        
        fun applyHeaderPatches(file: File) {
            if (pendingHeaderPatches.isEmpty()) return
            RandomAccessFile(file, "rw").use { raf ->
                pendingHeaderPatches.forEach { (at, bytes) ->
                    raf.seek(at)
                    raf.write(bytes)
                }
            }
        }
    }

    fun align(input: File, output: File): Boolean {
        if (!input.exists()) {
            AppLogger.e(TAG, "Input file does not exist: ${input.absolutePath}")
            return false
        }

        var alignedCount = 0
        var totalStored = 0

        return try {
            ZipFile(input).use { zipIn ->
                FileOutputStream(output).use { fos ->
                    val writer = ManualZipWriter(fos)

                    val entries = zipIn.entries().toList()
                        .sortedWith(
                            compareByDescending<ZipEntry> { it.name == "resources.arsc" }
                                .thenBy { it.name.startsWith("META-INF/") }
                                .thenBy { it.name }
                        )

                    for (entry in entries) {
                        if (entry.isDirectory) {
                            writer.addDirectory(entry.name)
                            continue
                        }

                        if (entry.method == ZipEntry.STORED || entry.name == "resources.arsc") {
                            totalStored++

                            val entrySize = entry.size
                            val entryCrc = if (entry.crc != -1L) entry.crc else computeCrc(zipIn, entry)
                            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)

                            
                            
                            val currentOffset = writer.offsetOfEntry
                            val dataOffsetNoExtra = currentOffset + LFH_FIXED_SIZE + nameBytes.size
                            val alignment = getEntryAlignment(entry.name)
                            val remainder = dataOffsetNoExtra % alignment
                            val padding = if (remainder == 0L) 0 else (alignment - remainder).toInt()

                            if (padding > 0) alignedCount++

                            zipIn.getInputStream(entry).use { stream ->
                                writer.addStored(entry.name, stream, entrySize, entryCrc,
                                    if (padding > 0) ByteArray(padding) else null)
                            }
                        } else {
                            zipIn.getInputStream(entry).use { stream ->
                                writer.addDeflated(entry.name, stream)
                            }
                        }
                    }

                    writer.close()
                    writer.applyHeaderPatches(output)
                }
            }

            AppLogger.d(TAG, "ZipAlign complete: $alignedCount/$totalStored STORED entries aligned")
            isValidZip(output)
        } catch (e: Exception) {
            AppLogger.e(TAG, "ZipAlign failed: ${e.message}", e)
            false
        }
    }

    fun verifyAlignment(apkFile: File): Boolean {
        try {
            RandomAccessFile(apkFile, "r").use { raf ->
                val entries = readCentralDirectory(raf) ?: run {
                    AppLogger.e(TAG, "Cannot parse zip central directory: ${apkFile.name}")
                    return false
                }
                val arsc = entries.firstOrNull { it.name == "resources.arsc" } ?: run {
                    AppLogger.w(TAG, "resources.arsc not found in APK")
                    return false
                }
                val isStored = arsc.method == ZipEntry.STORED
                val dataOffset = entryDataOffset(raf, arsc.localHeaderOffset)
                val isAligned = dataOffset % DEFAULT_ALIGNMENT == 0L
                AppLogger.d(TAG, "resources.arsc: stored=$isStored, dataOffset=$dataOffset, " +
                        "aligned=$isAligned (${dataOffset % DEFAULT_ALIGNMENT})")
                return isStored && isAligned
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Alignment verification failed: ${e.message}")
            return false
        }
    }

    fun verifyNativeLibAlignment(apkFile: File, alignment: Long = NATIVE_LIB_ALIGNMENT): Boolean {
        try {
            RandomAccessFile(apkFile, "r").use { raf ->
                val entries = readCentralDirectory(raf) ?: run {
                    AppLogger.e(TAG, "Cannot parse zip central directory: ${apkFile.name}")
                    return false
                }
                var nativeLibCount = 0
                var storedCount = 0
                for (entry in entries) {
                    if (!isNativeLibraryEntry(entry.name)) continue
                    nativeLibCount++
                    
                    
                    
                    
                    
                    
                    
                    if (entry.method != ZipEntry.STORED) continue
                    storedCount++
                    val dataOffset = entryDataOffset(raf, entry.localHeaderOffset)
                    if (dataOffset % alignment != 0L) {
                        AppLogger.w(TAG, "Native lib is not ${alignment / 1024}KB zip-aligned: ${entry.name} dataOffset=$dataOffset remainder=${dataOffset % alignment}")
                        return false
                    }
                }
                AppLogger.d(TAG, "Native lib zip alignment verified: $storedCount stored / $nativeLibCount total entries")
                return true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Native lib alignment verification failed: ${e.message}", e)
            return false
        }
    }

    private class CentralDirectoryEntry(
        val name: String,
        val method: Int,
        val localHeaderOffset: Long
    )

    








    private fun readCentralDirectory(raf: RandomAccessFile): List<CentralDirectoryEntry>? {
        val length = raf.length()
        if (length < 22L) return null
        val maxBack = minOf(length, 22L + 65535L).toInt()
        val tail = ByteArray(maxBack)
        raf.seek(length - maxBack)
        raf.readFully(tail)

        var eocdAt = -1
        for (i in maxBack - 22 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4B.toByte() &&
                tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
            ) {
                eocdAt = i
                break
            }
        }
        if (eocdAt < 0) return null

        
        
        
        val eocd = ByteBuffer.wrap(tail, eocdAt, 22).slice().order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = eocd.getShort(10).toInt() and 0xFFFF
        val cdSize = eocd.getInt(12).toLong() and 0xFFFFFFFFL
        val cdOffset = eocd.getInt(16).toLong() and 0xFFFFFFFFL
        
        if (entryCount == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) return null
        if (cdOffset < 0 || cdOffset + cdSize > length) return null

        val cd = ByteArray(cdSize.toInt())
        raf.seek(cdOffset)
        raf.readFully(cd)

        val entries = ArrayList<CentralDirectoryEntry>(entryCount)
        var p = 0
        repeat(entryCount) {
            if (p + 46 > cd.size) return null
            
            val e = ByteBuffer.wrap(cd, p, 46).slice().order(ByteOrder.LITTLE_ENDIAN)
            if (e.getInt(0) != 0x02014b50) return null
            val method = e.getShort(10).toInt() and 0xFFFF
            val nameLen = e.getShort(28).toInt() and 0xFFFF
            val extraLen = e.getShort(30).toInt() and 0xFFFF
            val commentLen = e.getShort(32).toInt() and 0xFFFF
            val lfhOffset = e.getInt(42).toLong() and 0xFFFFFFFFL
            if (p + 46 + nameLen > cd.size) return null
            val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
            entries += CentralDirectoryEntry(name, method, lfhOffset)
            p += 46 + nameLen + extraLen + commentLen
        }
        return entries
    }

    
    private fun entryDataOffset(raf: RandomAccessFile, lfhOffset: Long): Long {
        val lfh = ByteArray(LFH_FIXED_SIZE)
        raf.seek(lfhOffset)
        raf.readFully(lfh)
        val buf = ByteBuffer.wrap(lfh).order(ByteOrder.LITTLE_ENDIAN)
        val nameLen = buf.getShort(26).toInt() and 0xFFFF
        val extraLen = buf.getShort(28).toInt() and 0xFFFF
        return lfhOffset + LFH_FIXED_SIZE + nameLen + extraLen
    }

    private fun getEntryAlignment(name: String): Long {
        return if (isNativeLibraryEntry(name)) NATIVE_LIB_ALIGNMENT else DEFAULT_ALIGNMENT
    }

    private fun isNativeLibraryEntry(name: String): Boolean {
        return name.startsWith("lib/") && name.endsWith(".so")
    }
}
