package com.webtoapp.core.update

import com.webtoapp.core.logging.AppLogger
import com.webtoapp.core.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit






sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : UpdateDownloadState
    data object Verifying : UpdateDownloadState
    data class Done(val file: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

object ApkUpdateInstaller {

    private const val TAG = "ApkUpdateInstaller"

    
    const val UPDATE_APK_DIR = "update_apks"

    








    private const val MIN_ROUTE_SPEED_BPS = 64L * 1024
    private const val STALL_GRACE_MS = 10_000L
    private const val ROUTE_WARMUP_MS = 8_000L

    
    private const val ROUTE_READ_TIMEOUT_MS = 30_000L

    private class RouteStallException(val speedBps: Long) :
        IOException("route too slow (${speedBps / 1024} KB/s)")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    
    @Volatile
    private var activeJob: Job? = null

    
    private fun targetDir(context: android.content.Context): File {
        val base = context.getExternalFilesDir(null)
            ?: File(context.filesDir, "external").apply { mkdirs() }
        return File(base, UPDATE_APK_DIR).apply { mkdirs() }
    }

    















    fun download(
        context: android.content.Context,
        url: String,
        version: String,
        expectedSha256: String? = null,
        onState: (UpdateDownloadState) -> Unit
    ): Job {
        activeJob?.cancel()
        val appContext = context.applicationContext
        val job = scope.launch {
            
            
            
            
            val candidates = com.webtoapp.core.network.GitHubMirror.proxiedCn(url)
            var lastError: Exception? = null
            candidates.forEachIndexed { index, candidate ->
                
                
                val enforceSpeedFloor = index < candidates.lastIndex
                try {
                    onState(UpdateDownloadState.Downloading(0L, -1L, 0L))
                    val file = streamToFile(appContext, candidate, version, enforceSpeedFloor, onState)
                    if (expectedSha256 != null) {
                        onState(UpdateDownloadState.Verifying)
                        val actual = sha256Of(file)
                        if (actual == null || !actual.equals(expectedSha256, ignoreCase = true)) {
                            AppLogger.e(
                                TAG,
                                "APK integrity check failed via $candidate: " +
                                    "expected=$expectedSha256 actual=$actual"
                            )
                            
                            
                            
                            
                            
                            file.delete()
                            lastError = IllegalStateException("sha256-mismatch")
                            return@forEachIndexed
                        }
                        AppLogger.i(TAG, "APK integrity verified (sha256 match)")
                    }
                    onState(UpdateDownloadState.Done(file))
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Update download failed via $candidate: ${e.message}")
                    lastError = e
                }
            }
            onState(
                UpdateDownloadState.Failed(
                    lastError?.message ?: lastError?.javaClass?.simpleName ?: "all routes failed"
                )
            )
        }
        activeJob = job
        return job
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    





    private suspend fun streamToFile(
        context: android.content.Context,
        url: String,
        version: String,
        enforceSpeedFloor: Boolean,
        onState: (UpdateDownloadState) -> Unit
    ): File {
        val dir = targetDir(context)
        val fileName = "web-to-app-$version.apk"
        val target = File(dir, fileName)

        
        
        val client = NetworkModule.downloadClient.newBuilder()
            .readTimeout(ROUTE_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        fun openStream(offset: Long) = client.newCall(
            Request.Builder().url(url).apply {
                if (offset > 0) header("Range", "bytes=$offset-")
            }.build()
        ).execute()

        var resumeFrom = target.takeIf { it.exists() }?.length() ?: 0L
        var response = openStream(resumeFrom)
        if (response.code == 416 && resumeFrom > 0) {
            
            
            response.close()
            target.delete()
            resumeFrom = 0L
            response = openStream(0L)
        }
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("HTTP ${response.code}")
        }
        val body = response.body ?: throw IllegalStateException("empty response body")
        
        val appending = resumeFrom > 0 && response.code == 206
        val offset = if (appending) resumeFrom else 0L
        val contentLength = body.contentLength()
        val total = if (contentLength > 0) offset + contentLength else -1L

        body.byteStream().use { input ->
            FileOutputStream(target, appending).buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = offset
                var windowStartBytes = offset
                var windowStartTime = System.currentTimeMillis()
                val transferStart = windowStartTime
                var lastEmit = 0L
                var lowSpeedSince = 0L
                while (true) {
                    
                    kotlinx.coroutines.coroutineScope { ensureActive() }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - windowStartTime
                    if (now - lastEmit >= 1000L || (total > 0 && downloaded == total)) {
                        val speed = if (elapsed > 0) {
                            (downloaded - windowStartBytes) * 1000L / elapsed
                        } else 0L
                        onState(UpdateDownloadState.Downloading(downloaded, total, speed))
                        lastEmit = now
                        
                        windowStartBytes = downloaded
                        windowStartTime = now

                        if (enforceSpeedFloor && now - transferStart > ROUTE_WARMUP_MS) {
                            if (speed < MIN_ROUTE_SPEED_BPS) {
                                if (lowSpeedSince == 0L) lowSpeedSince = now
                                if (now - lowSpeedSince >= STALL_GRACE_MS) {
                                    throw RouteStallException(speed)
                                }
                            } else {
                                lowSpeedSince = 0L
                            }
                        }
                    }
                }
                output.flush()
                
                onState(UpdateDownloadState.Downloading(downloaded, total.coerceAtLeast(downloaded), 0L))
            }
        }
        return target
    }

    private fun sha256Of(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to compute sha256: ${e.message}")
            null
        }
    }
}
