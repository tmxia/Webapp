package com.webtoapp.core.webview

import android.content.Context
import android.os.Build
import com.webtoapp.core.download.DependencyDownloadEngine
import com.webtoapp.core.logging.AppLogger
import com.webtoapp.util.SafeZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream











object CronetDependencyManager {

    private const val TAG = "CronetDependencyManager"

    
    const val CRONET_ARTIFACT_VERSION = "143.7445.0"

    
    const val CRONET_LIB_VERSION = "143.0.7445.0"

    const val LIB_FILE_NAME = "libcronet.$CRONET_LIB_VERSION.so"

    private const val MAX_RETRY_PER_URL = 2
    private const val RETRY_DELAY_MS = 2000L

    private val downloadMutex = Mutex()

    
    
    
    
    
    private val PRIMARY_URL =
        "https://maven.google.com/org/chromium/net/cronet-embedded/$CRONET_ARTIFACT_VERSION/cronet-embedded-$CRONET_ARTIFACT_VERSION.aar"

    
    private val CN_MIRROR_URL =
        "https://maven.aliyun.com/repository/google/org/chromium/net/cronet-embedded/$CRONET_ARTIFACT_VERSION/cronet-embedded-$CRONET_ARTIFACT_VERSION.aar"

    private fun downloadUrls(): List<String> = listOf(CN_MIRROR_URL, PRIMARY_URL)

    




    private const val CRONET_AAR_SHA256 =
        "afdd7af7568e9758e3690c3a4a228f30993ac3e4ef0f94c72bb68503c0167697"

    fun getDepsDir(context: Context): File =
        File(context.filesDir, "cronet_deps").also { it.mkdirs() }

    fun getDeviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    fun getLibDir(context: Context): File =
        File(getDepsDir(context), getDeviceAbi()).also { it.mkdirs() }

    fun getLibFile(context: Context): File = File(getLibDir(context), LIB_FILE_NAME)

    




    fun resolveCronetLib(context: Context): File? {
        val native = File(context.applicationInfo.nativeLibraryDir, LIB_FILE_NAME)
        if (native.isFile && native.length() > 0L) return native
        val downloaded = getLibFile(context)
        if (downloaded.isFile && downloaded.length() > 0L) return downloaded
        return null
    }

    fun isCronetReady(context: Context): Boolean = resolveCronetLib(context) != null

    fun clearCache(context: Context) {
        getDepsDir(context).deleteRecursively()
        AppLogger.i(TAG, "Cronet dependency cache cleared")
    }

    fun getCacheSize(context: Context): Long {
        return getDepsDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun downloadCronetRuntime(context: Context): Boolean = downloadMutex.withLock {
        if (isCronetReady(context)) return@withLock true
        withContext(Dispatchers.IO) {
            val urls = downloadUrls()
            val archive = File(context.cacheDir, "cronet_temp.aar")
            val downloaded = try {
                DependencyDownloadEngine.downloadFileWithFallback(
                    urls = urls,
                    destFile = archive,
                    displayName = "Cronet $CRONET_ARTIFACT_VERSION",
                    context = context,
                    maxRetryPerUrl = MAX_RETRY_PER_URL,
                    retryDelayMs = RETRY_DELAY_MS,
                    expectedSha256For = { _ -> CRONET_AAR_SHA256 }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cronet download failed", e)
                false
            }
            if (!downloaded) return@withContext false
            try {
                extractCronetLib(context, archive)
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cronet extraction failed", e)
                false
            } finally {
                try { archive.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun extractCronetLib(context: Context, archive: File) {
        val abi = getDeviceAbi()
        val wanted = "jni/$abi/$LIB_FILE_NAME"
        val outFile = getLibFile(context)
        outFile.parentFile?.mkdirs()

        
        getDepsDir(context).listFiles()?.forEach { abiDir ->
            if (abiDir.isDirectory) {
                abiDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.startsWith("libcronet.") && f.name != LIB_FILE_NAME) {
                        try { f.delete() } catch (_: Exception) {}
                    }
                }
            }
        }

        var found = false
        val guard = SafeZip.EntryGuard()
        ZipInputStream(archive.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                guard.onEntry()
                if (!entry.isDirectory && entry.name == wanted) {
                    val tmp = File(outFile.parentFile, "$LIB_FILE_NAME.tmp")
                    tmp.outputStream().buffered().use { fos ->
                        guard.copyTo(zis, fos)
                    }
                    if (tmp.length() <= 0L) {
                        tmp.delete()
                        throw IllegalStateException("Empty Cronet library entry: ${entry.name}")
                    }
                    if (outFile.exists()) outFile.delete()
                    if (!tmp.renameTo(outFile)) {
                        tmp.copyTo(outFile, overwrite = true)
                        tmp.delete()
                    }
                    found = true
                    AppLogger.i(TAG, "Extracted ${entry.name} -> ${outFile.absolutePath} (${outFile.length()} bytes)")
                }
                entry = zis.nextEntry
            }
        }
        if (!found) {
            throw IllegalStateException("Cronet library not found in AAR (ABI: $abi)")
        }
    }
}
