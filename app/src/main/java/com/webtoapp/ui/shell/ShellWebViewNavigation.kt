package com.webtoapp.ui.shell

import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.webtoapp.core.engine.BrowserSurface

object ShellWebViewNavigation {

    private const val EXIT_CONFIRM_WINDOW_MS = 2500L

    





    internal const val HISTORY_STEP_GUARD_MS = 400L

    @Volatile
    private var lastExitPromptAtMs = 0L

    @Volatile
    private var lastHistoryStepAtMs = 0L

    








    private fun confirmExitThenFinish(activity: AppCompatActivity, confirm: Boolean) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!confirm) {
            if (!shouldFinishImmediately(now, lastHistoryStepAtMs)) return
            lastExitPromptAtMs = 0L
            activity.finish()
            return
        }
        if (now - lastExitPromptAtMs <= EXIT_CONFIRM_WINDOW_MS) {
            lastExitPromptAtMs = 0L
            activity.finish()
        } else {
            lastExitPromptAtMs = now
            android.widget.Toast.makeText(
                activity,
                com.webtoapp.core.i18n.Strings.pressAgainToExit,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun noteHistoryStep() {
        lastHistoryStepAtMs = android.os.SystemClock.uptimeMillis()
    }

    




    internal fun shouldFinishImmediately(
        nowMs: Long,
        lastHistoryStepAtMs: Long,
        guardMs: Long = HISTORY_STEP_GUARD_MS,
    ): Boolean {
        if (lastHistoryStepAtMs <= 0L) return true
        return nowMs - lastHistoryStepAtMs > guardMs
    }

    fun goBackOrFinish(activity: AppCompatActivity, webView: WebView?) {
        goBackOrFinish(activity, webView, useJsHistoryBack = false)
    }

    fun goBackOrFinish(
        activity: AppCompatActivity,
        webView: WebView?,
        useJsHistoryBack: Boolean,
        confirmExit: Boolean = true,
    ) {
        val wv = webView ?: run {
            activity.finish()
            return
        }

        if (useJsHistoryBack) {
            goBackViaJsHistoryBack(activity, wv, confirmExit)
            return
        }
        goBackNative(activity, wv, confirmExit)
    }

    






    fun goBackOrFinish(
        activity: AppCompatActivity,
        surface: BrowserSurface?,
        useJsHistoryBack: Boolean = false,
        confirmExit: Boolean = true,
    ) {
        val wv = surface?.webView
        if (wv != null) {
            goBackOrFinish(activity, wv, useJsHistoryBack, confirmExit)
            return
        }
        if (surface != null && surface.canGoBack()) {
            noteHistoryStep()
            surface.goBack()
            return
        }
        confirmExitThenFinish(activity, confirmExit)
    }

    private fun goBackViaJsHistoryBack(
        activity: AppCompatActivity,
        webView: WebView,
        confirmExit: Boolean,
    ) {
        
        
        
        
        
        
        
        dispatchBackAction(activity, webView, resolveBackActionFor(webView), useJsHistory = true, confirmExit = confirmExit)
    }

    private fun goBackNative(activity: AppCompatActivity, wv: WebView, confirmExit: Boolean) {
        dispatchBackAction(activity, wv, resolveBackActionFor(wv), useJsHistory = false, confirmExit = confirmExit)
    }

    private fun dispatchBackAction(
        activity: AppCompatActivity,
        webView: WebView,
        action: BackAction,
        useJsHistory: Boolean,
        confirmExit: Boolean,
    ) {
        when (action) {
            BackAction.FINISH -> confirmExitThenFinish(activity, confirmExit)
            BackAction.GO_BACK -> {
                noteHistoryStep()
                if (useJsHistory) {
                    webView.evaluateJavascript("history.back();", null)
                } else {
                    webView.goBack()
                }
            }
            BackAction.SKIP_PREVIOUS -> {
                noteHistoryStep()
                webView.goBackOrForward(-2)
            }
        }
    }

    private fun resolveBackActionFor(wv: WebView): BackAction {
        val list = wv.copyBackForwardList()
        val currentIndex = list.currentIndex
        val currentItem = list.getItemAtIndex(currentIndex)
        val previousItem = if (currentIndex > 0) list.getItemAtIndex(currentIndex - 1) else null
        val beforePreviousItem = if (currentIndex > 1) list.getItemAtIndex(currentIndex - 2) else null
        val currentUrls = listOf(
            currentItem?.url.orEmpty(),
            currentItem?.originalUrl.orEmpty(),
            wv.url.orEmpty(),
            wv.originalUrl.orEmpty()
        )
        val previousUrls = listOf(
            previousItem?.url.orEmpty(),
            previousItem?.originalUrl.orEmpty()
        )
        val beforePreviousUrls = listOf(
            beforePreviousItem?.url.orEmpty(),
            beforePreviousItem?.originalUrl.orEmpty()
        )

        return resolveBackAction(wv.canGoBack(), currentIndex, currentUrls, previousUrls, beforePreviousUrls)
    }

    internal fun shouldFinishInsteadOfBack(currentUrl: String, previousUrl: String): Boolean {
        return resolveBackAction(
            canGoBack = true,
            currentIndex = 1,
            currentUrls = listOf(currentUrl),
            previousUrls = listOf(previousUrl),
            beforePreviousUrls = emptyList()
        ) == BackAction.FINISH
    }

    internal fun resolveBackAction(
        canGoBack: Boolean,
        currentIndex: Int,
        currentUrls: List<String>,
        previousUrls: List<String>,
        beforePreviousUrls: List<String> = emptyList()
    ): BackAction {
        if (!canGoBack || currentIndex <= 0) return BackAction.FINISH

        val currentHasBlank = currentUrls.any(::isBlankPage)
        val currentHasError = currentUrls.any(::isGeneratedErrorPage)
        val currentHasLocal = currentUrls.any(::isLocalRuntimeUrl)
        val previousHasBlank = previousUrls.any(::isBlankPage)
        val previousHasError = previousUrls.any(::isGeneratedErrorPage)
        val previousHasLocal = previousUrls.any(::isLocalRuntimeUrl)
        val previousHasRemote = previousUrls.any(::isRemoteWebUrl)
        val beforePreviousHasRealPage = beforePreviousUrls.any(::isRealHistoryPage)

        if (currentHasError && previousHasRemote) {
            return if (beforePreviousHasRealPage && currentIndex > 1) {
                BackAction.SKIP_PREVIOUS
            } else {
                BackAction.FINISH
            }
        }

        
        
        
        
        if (previousHasBlank || previousHasError) {
            return if (beforePreviousHasRealPage && currentIndex > 1) {
                BackAction.SKIP_PREVIOUS
            } else {
                BackAction.FINISH
            }
        }

        val shouldFinish = currentHasBlank && previousHasLocal ||
            currentHasError && previousHasLocal

        return if (shouldFinish) BackAction.FINISH else BackAction.GO_BACK
    }

    internal enum class BackAction {
        FINISH,
        GO_BACK,
        SKIP_PREVIOUS
    }

    private fun isBlankPage(url: String): Boolean {
        return url.isBlank() || url == "about:blank"
    }

    private fun isGeneratedErrorPage(url: String): Boolean {
        return url.startsWith("data:text/html", ignoreCase = true) ||
            url.startsWith("data:text/plain", ignoreCase = true)
    }

    private fun isLocalRuntimeUrl(url: String): Boolean {
        return url.startsWith("http://127.0.0.1:", ignoreCase = true) ||
            url.startsWith("http://localhost:", ignoreCase = true)
    }

    private fun isRemoteWebUrl(url: String): Boolean {
        return (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)) &&
            !isLocalRuntimeUrl(url)
    }

    private fun isRealHistoryPage(url: String): Boolean {
        return url.isNotBlank() &&
            !isBlankPage(url) &&
            !isGeneratedErrorPage(url)
    }
}
