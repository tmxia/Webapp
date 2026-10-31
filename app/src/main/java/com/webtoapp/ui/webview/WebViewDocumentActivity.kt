package com.webtoapp.ui.webview

import android.os.Bundle
import java.util.Collections
import java.util.WeakHashMap









class WebViewDocumentActivity : WebViewActivity() {

    companion object {
        private val live = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<WebViewDocumentActivity, Boolean>())
        )

        



        fun finishAllDocumentTasks() {
            val snapshot = synchronized(live) { live.toList() }
            snapshot.forEach { activity ->
                if (activity.launchedFromShortcut || activity.isFinishing) return@forEach
                activity.finishAndRemoveTask()
            }
        }
    }

    internal var launchedFromShortcut: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        launchedFromShortcut = intent.getBooleanExtra(EXTRA_SHORTCUT_TASK, false)
        live.add(this)
        super.onCreate(savedInstanceState)
        PreviewSessions.onActivityReady(this)
    }

    override fun onDestroy() {
        PreviewSessions.onActivityGone(this)
        live.remove(this)
        super.onDestroy()
    }
}
