package com.webtoapp.core.webview

import android.webkit.PermissionRequest
import com.webtoapp.core.logging.AppLogger









object WebPermissionRequests {

    private const val TAG = "WebPermission"

    




    fun settle(request: PermissionRequest, granted: Boolean): Boolean {
        return try {
            if (granted) request.grant(request.resources) else request.deny()
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "settle granted=$granted failed: ${e.message}")
            if (granted) {
                try {
                    request.deny()
                } catch (denyError: Exception) {
                    AppLogger.w(TAG, "deny after failed grant failed: ${denyError.message}")
                }
            }
            false
        }
    }

    




    fun shouldReloadAfterRuntimePermission(
        runtimeGranted: Boolean,
        webRequestSettled: Boolean,
    ): Boolean = runtimeGranted && !webRequestSettled
}






class CapturePermissionBridge {

    private var pending: PermissionRequest? = null
    private var cancelled = false
    private var awaitingRuntime = false

    fun remember(request: PermissionRequest) {
        pending = request
        cancelled = false
        awaitingRuntime = true
    }

    fun clear() {
        pending = null
        cancelled = false
        awaitingRuntime = false
    }

    fun onCanceled(request: PermissionRequest) {
        if (pending === request) {
            cancelled = true
            pending = null
        }
    }

    
    fun onRuntimeResult(runtimeGranted: Boolean): Boolean {
        val request = pending
        val wasCancelled = cancelled
        val armed = awaitingRuntime
        pending = null
        cancelled = false
        awaitingRuntime = false
        if (!armed) return false
        val settled = request != null && !wasCancelled &&
            WebPermissionRequests.settle(request, runtimeGranted)
        return WebPermissionRequests.shouldReloadAfterRuntimePermission(runtimeGranted, settled)
    }
}
