package com.webtoapp.util

import com.webtoapp.core.logging.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream









object SafeZip {

    private const val TAG = "SafeZip"

    const val DEFAULT_MAX_ENTRIES = 20_000
    const val DEFAULT_MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024

    
    class ZipBombException(kind: Kind, message: String) : IOException(message) {
        enum class Kind { ENTRY_COUNT, TOTAL_SIZE }
        val kind: Kind = kind
    }

    





    class EntryGuard(
        private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES
    ) {
        var totalBytes = 0L
            private set

        fun onEntry() {
            entryCount++
            if (entryCount > maxEntries) {
                throw ZipBombException(ZipBombException.Kind.ENTRY_COUNT, "Archive entry count exceeds $maxEntries")
            }
        }

        fun onBytes(count: Int) {
            if (count <= 0) return
            totalBytes += count
            if (totalBytes > maxTotalBytes) {
                throw ZipBombException(
                    ZipBombException.Kind.TOTAL_SIZE,
                    "Archive extracted size exceeds ${maxTotalBytes / 1024 / 1024} MB"
                )
            }
        }

        
        fun copyTo(input: InputStream, out: OutputStream) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                onBytes(read)
                out.write(buffer, 0, read)
            }
        }

        private var entryCount = 0
    }

    




    fun safeChild(baseDir: File, entryName: String): File? {
        if (entryName.isBlank()) return null
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
            AppLogger.w(TAG, "Skip unsafe archive entry: $entryName")
            return null
        }
        val target = File(baseDir, normalized)
        val baseCanonical = baseDir.canonicalFile
        val targetCanonical = target.canonicalFile
        
        
        return if (targetCanonical.path.startsWith(baseCanonical.path + File.separator)) {
            targetCanonical
        } else {
            AppLogger.w(TAG, "Skip unsafe archive entry: $entryName")
            null
        }
    }

    





    fun extractAll(
        zis: ZipInputStream,
        destDir: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        filter: (String) -> Boolean = { true },
        observe: (ZipEntry) -> Unit = {}
    ): Long {
        val guard = EntryGuard(maxEntries, maxTotalBytes)
        var entry = zis.nextEntry
        while (entry != null) {
            guard.onEntry()
            observe(entry)
            if (filter(entry.name)) {
                if (entry.isDirectory) {
                    safeChild(destDir, entry.name)?.mkdirs()
                } else {
                    val outFile = safeChild(destDir, entry.name)
                    if (outFile != null) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> guard.copyTo(zis, out) }
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return guard.totalBytes
    }

    
    fun hasOwnerExecBit(mode: Long): Boolean = (mode and 0b001_000_000L) != 0L
}
