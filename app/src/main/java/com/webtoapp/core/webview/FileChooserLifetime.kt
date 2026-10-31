package com.webtoapp.core.webview

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebView






















object FileChooserLifetime {

    @Volatile
    private var open = false

    private var permissionHolds = 0

    private var resumeProbeSkips = 0

    private var parkedWebView: WebView? = null

    private var parkedCallback: ValueCallback<Array<Uri>>? = null

    private var parkedCameraUri: Uri? = null

    private val reattached = HashSet<Int>()

    fun markOpen() {
        open = true
    }

    fun markClosed() {
        open = false
        parkedCallback = null
        parkedCameraUri = null
        val wv = parkedWebView
        if (wv == null || wv.parent != null) {
            parkedWebView = null
        }
    }

    
    fun parkWebView(webView: WebView) {
        parkedWebView = webView
        open = true
    }

    fun holdCallback(callback: ValueCallback<Array<Uri>>?) {
        parkedCallback = callback
        if (callback != null) open = true
    }

    fun holdCamera(uri: Uri?) {
        parkedCameraUri = uri
    }

    fun callback(): ValueCallback<Array<Uri>>? = parkedCallback

    fun cameraUri(): Uri? = parkedCameraUri

    



    fun takeDetachedWebView(): WebView? {
        val wv = parkedWebView ?: return null
        if (wv.parent != null) return null
        parkedWebView = null
        reattached.add(System.identityHashCode(wv))
        return wv
    }

    fun consumeReattached(webView: WebView): Boolean =
        reattached.remove(System.identityHashCode(webView))

    
    fun shouldRetain(webView: WebView): Boolean = open && parkedWebView === webView

    




    fun runPermissionDialog(launch: () -> Unit): Boolean {
        synchronized(this) {
            permissionHolds++
            resumeProbeSkips++
        }
        return try {
            launch()
            true
        } catch (e: Exception) {
            synchronized(this) {
                if (permissionHolds > 0) permissionHolds--
                if (resumeProbeSkips > 0) resumeProbeSkips--
            }
            false
        }
    }

    
    fun endPermissionDialog() {
        synchronized(this) {
            if (permissionHolds > 0) permissionHolds--
        }
    }

    




    fun consumeResumeProbeSkip(): Boolean {
        synchronized(this) {
            if (resumeProbeSkips <= 0) return false
            resumeProbeSkips--
            return true
        }
    }

    fun isOpen(): Boolean = open || synchronized(this) { permissionHolds > 0 }
}
