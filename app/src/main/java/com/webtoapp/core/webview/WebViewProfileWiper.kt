package com.webtoapp.core.webview

import android.content.Context
import com.webtoapp.core.logging.AppLogger
import java.io.File
















internal object WebViewProfileWiper {

    private const val TAG = "WebViewProfileWiper"

    fun wipeIfProviderInactive(context: Context): Boolean {
        val appInfo = context.applicationInfo ?: return false
        val dataDir = appInfo.dataDir?.let(::File) ?: return false
        return wipe(
            dataDir = dataDir,
            cacheDir = context.cacheDir,
            providerInitialized = isSystemWebViewProviderInitialized()
        )
    }

    





    fun wipe(dataDir: File, cacheDir: File, providerInitialized: Boolean): Boolean {
        if (providerInitialized) {
            AppLogger.i(TAG, "WebView provider already live; leaving the on-disk profile in place")
            return false
        }
        val targets = profilePaths(dataDir, cacheDir)
        targets.forEach { path ->
            if (!path.exists()) return@forEach
            val removed = deleteTree(path)
            AppLogger.i(TAG, "Fresh-session profile ${path.name} removed=$removed")
        }
        val leftover = targets.filter { it.exists() }
        if (leftover.isNotEmpty()) {
            AppLogger.w(TAG, "Fresh-session profile wipe left ${leftover.joinToString { it.name }}")
            return false
        }
        return true
    }

    fun profilePaths(dataDir: File, cacheDir: File): List<File> = listOf(
        File(dataDir, "app_webview"),
        
        File(dataDir, "app_hws_webview"),
        File(cacheDir, "WebView"),
        File(cacheDir, "org.chromium.android_webview"),
        File(dataDir, "databases/webview.db"),
        File(dataDir, "databases/webview.db-journal"),
        File(dataDir, "databases/webviewCache.db"),
        File(dataDir, "databases/webviewCache.db-journal"),
    )

    




    fun isSystemWebViewProviderInitialized(): Boolean {
        return try {
            val factory = Class.forName("android.webkit.WebViewFactory")
            val field = factory.getDeclaredField("sProviderInstance")
            field.isAccessible = true
            field.get(null) != null
        } catch (t: Throwable) {
            AppLogger.w(TAG, "WebView provider probe failed; skipping directory wipe", t)
            true
        }
    }

    private fun deleteTree(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteTree(child) }
        }
        if (!file.exists()) return true
        if (file.delete()) return true
        file.setWritable(true)
        return file.delete()
    }
}
