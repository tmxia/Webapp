package com.webtoapp.ui.shared

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.webtoapp.core.logging.AppLogger
import com.webtoapp.data.model.KeyboardAdjustMode
import java.util.Collections
import java.util.WeakHashMap

object WindowHelper {

    private val manualImeInstalled =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))

    private val imeOcclusionListeners =
        java.util.concurrent.CopyOnWriteArrayList<(View, Int) -> Unit>()
    private val lastImeOcclusionPx =
        Collections.synchronizedMap(WeakHashMap<View, Int>())

    





    fun addImeOcclusionListener(listener: (View, Int) -> Unit): () -> Unit {
        imeOcclusionListeners.add(listener)
        return { imeOcclusionListeners.remove(listener) }
    }

    private fun publishImeOcclusion(contentView: View, px: Int) {
        val value = px.coerceAtLeast(0)
        if (lastImeOcclusionPx[contentView] == value) return
        lastImeOcclusionPx[contentView] = value
        for (listener in imeOcclusionListeners) {
            listener(contentView, value)
        }
    }

    fun applyStatusBarColor(
        activity: Activity,
        colorMode: String,
        customColor: String?,
        darkIcons: Boolean?,
        isDarkTheme: Boolean,
        backgroundAlpha: Float = 1f
    ) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        val alpha = backgroundAlpha.coerceIn(0f, 1f)
        
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
        }
        
        
        val colorMode = if (colorMode == "TRANSPARENT" && isClassicKeyboardResizeWindow(activity.window)) {
            "THEME"
        } else {
            colorMode
        }

        when (colorMode) {
            "TRANSPARENT" -> {
                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                val useDarkIcons = darkIcons ?: !isDarkTheme
                controller.isAppearanceLightStatusBars = useDarkIcons
            }
            "CUSTOM" -> {
                val baseColor = try {
                    android.graphics.Color.parseColor(customColor ?: "#FFFFFF")
                } catch (e: Exception) {
                    android.graphics.Color.WHITE
                }
                activity.window.statusBarColor = applyAlpha(baseColor, alpha)
                val useDarkIcons = darkIcons ?: isColorLight(baseColor)
                controller.isAppearanceLightStatusBars = useDarkIcons
            }
            else -> {
                if (isDarkTheme) {
                    val base = android.graphics.Color.parseColor("#1C1B1F")
                    activity.window.statusBarColor = applyAlpha(base, alpha)
                    controller.isAppearanceLightStatusBars = false
                } else {
                    val base = android.graphics.Color.parseColor("#FFFBFE")
                    activity.window.statusBarColor = applyAlpha(base, alpha)
                    controller.isAppearanceLightStatusBars = true
                }
            }
        }

        controller.isAppearanceLightNavigationBars = controller.isAppearanceLightStatusBars
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return android.graphics.Color.argb(
            a,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )
    }

    fun isColorLight(color: Int): Boolean {
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
        return luminance > 0.5
    }

    fun applyImmersiveFullscreen(
        activity: Activity,
        enabled: Boolean,
        hideNavBar: Boolean = true,
        isDarkTheme: Boolean = false,
        showStatusBar: Boolean = false,
        statusBarColorMode: String = "THEME",
        statusBarCustomColor: String? = null,
        statusBarDarkIcons: Boolean? = null,
        statusBarBgType: String = "COLOR",
        keyboardAdjustMode: KeyboardAdjustMode? = null,
        tag: String = "WindowHelper"
    ) {
        try {

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                activity.window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            
            
            
            
            
            
            
            val classicKeyboardResize = isClassicKeyboardResize(keyboardAdjustMode)
            val effectiveStatusBarColorMode =
                if (classicKeyboardResize && statusBarColorMode == "TRANSPARENT") "THEME" else statusBarColorMode
            val effectiveStatusBarBgType =
                if (classicKeyboardResize && statusBarBgType == "IMAGE") "COLOR" else statusBarBgType

            WindowInsetsControllerCompat(activity.window, activity.window.decorView).let { controller ->
                var decorFitsSystemWindows = true

                
                
                
                
                
                
                
                
                
                if (enabled && !showStatusBar && classicKeyboardResize) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }

                if (enabled) {
                    if (!classicKeyboardResize) {
                        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    }
                    val shouldShowStatusBar = showStatusBar

                    if (shouldShowStatusBar) {
                        decorFitsSystemWindows = classicKeyboardResize
                        WindowCompat.setDecorFitsSystemWindows(activity.window, classicKeyboardResize)
                        controller.show(WindowInsetsCompat.Type.statusBars())

                        if (effectiveStatusBarBgType == "IMAGE") {
                            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                            val useDarkIcons = statusBarDarkIcons ?: !isDarkTheme
                            controller.isAppearanceLightStatusBars = useDarkIcons
                        } else {
                            when (effectiveStatusBarColorMode) {
                                "CUSTOM" -> {
                                    val color = try {
                                        android.graphics.Color.parseColor(statusBarCustomColor ?: "#000000")
                                    } catch (e: Exception) {
                                        android.graphics.Color.BLACK
                                    }
                                    activity.window.statusBarColor = color
                                    val useDarkIcons = statusBarDarkIcons ?: isColorLight(color)
                                    controller.isAppearanceLightStatusBars = useDarkIcons
                                }
                                "TRANSPARENT" -> {
                                    activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                                    val useDarkIcons = statusBarDarkIcons ?: !isDarkTheme
                                    controller.isAppearanceLightStatusBars = useDarkIcons
                                }
                                else -> {
                                    if (isDarkTheme) {
                                        activity.window.statusBarColor = android.graphics.Color.parseColor("#1C1B1F")
                                        controller.isAppearanceLightStatusBars = false
                                    } else {
                                        activity.window.statusBarColor = android.graphics.Color.parseColor("#FFFBFE")
                                        controller.isAppearanceLightStatusBars = true
                                    }
                                }
                            }
                        }
                    } else {
                        decorFitsSystemWindows = classicKeyboardResize
                        WindowCompat.setDecorFitsSystemWindows(activity.window, classicKeyboardResize)
                        activity.window.statusBarColor = if (classicKeyboardResize) {
                            
                            android.graphics.Color.parseColor(if (isDarkTheme) "#1C1B1F" else "#FFFBFE")
                        } else {
                            android.graphics.Color.TRANSPARENT
                        }
                        controller.hide(WindowInsetsCompat.Type.statusBars())
                    }

                    
                    
                    
                    
                    val barsBehavior = if (shouldShowStatusBar) {
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    } else {
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }

                    if (hideNavBar) {
                        controller.hide(WindowInsetsCompat.Type.navigationBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.navigationBars())
                    }
                    controller.systemBarsBehavior = barsBehavior
                } else {
                    decorFitsSystemWindows = classicKeyboardResize
                    WindowCompat.setDecorFitsSystemWindows(activity.window, classicKeyboardResize)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    
                    
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    if (!classicKeyboardResize) {
                        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    }

                    if (effectiveStatusBarBgType == "IMAGE") {
                        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                        val useDarkIcons = statusBarDarkIcons ?: !isDarkTheme
                        controller.isAppearanceLightStatusBars = useDarkIcons
                        controller.isAppearanceLightNavigationBars = useDarkIcons
                    } else {
                        when (effectiveStatusBarColorMode) {
                            "CUSTOM" -> {
                                val color = try {
                                    android.graphics.Color.parseColor(statusBarCustomColor ?: "#000000")
                                } catch (e: Exception) {
                                    android.graphics.Color.BLACK
                                }
                                activity.window.statusBarColor = color
                                val useDarkIcons = statusBarDarkIcons ?: isColorLight(color)
                                controller.isAppearanceLightStatusBars = useDarkIcons
                                controller.isAppearanceLightNavigationBars = useDarkIcons
                            }
                            "TRANSPARENT" -> {
                                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                                val useDarkIcons = statusBarDarkIcons ?: !isDarkTheme
                                controller.isAppearanceLightStatusBars = useDarkIcons
                                controller.isAppearanceLightNavigationBars = useDarkIcons
                            }
                            else -> {
                                applyStatusBarColor(
                                    activity,
                                    effectiveStatusBarColorMode,
                                    statusBarCustomColor,
                                    statusBarDarkIcons,
                                    isDarkTheme
                                )
                            }
                        }
                    }
                    activity.window.decorView.post {
                        ViewCompat.requestApplyInsets(activity.window.decorView)
                    }
                }
                if (classicKeyboardResize) {
                    
                    
                    activity.window.navigationBarColor = activity.window.statusBarColor
                }
                applyKeyboardMode(activity, keyboardAdjustMode, tag, decorFitsSystemWindows)
                if (classicKeyboardResize && enabled) {
                    
                    
                    
                    
                    
                    
                    
                    
                    
                    listOf(350L, 900L, 1600L).forEach { delayMs ->
                        activity.window.decorView.postDelayed({
                            try {
                                reAssertImmersiveFlags(activity.window, hideNavBar, !showStatusBar)
                            } catch (e: Exception) {
                                AppLogger.w(tag, "re-assert immersive flags failed", e)
                            }
                        }, delayMs)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(tag, "applyImmersiveFullscreen failed", e)
        }
    }

    




    @Suppress("DEPRECATION")
    private fun reAssertImmersiveFlags(window: Window, hideNavBar: Boolean, hideStatusBar: Boolean) {
        val decorView = window.decorView
        val target = decorView.systemUiVisibility
        val required = (if (hideStatusBar) View.SYSTEM_UI_FLAG_FULLSCREEN else 0) or
            (if (hideNavBar) View.SYSTEM_UI_FLAG_HIDE_NAVIGATION else 0)
        if (required != 0 && (target and required) != required) {
            decorView.systemUiVisibility = target or required or
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        
        
        if (hideStatusBar &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun isClassicKeyboardResize(keyboardAdjustMode: KeyboardAdjustMode?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        return (keyboardAdjustMode ?: KeyboardAdjustMode.RESIZE) == KeyboardAdjustMode.RESIZE
    }

    
    private fun isClassicKeyboardResizeWindow(window: Window): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        return window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE != 0
    }

    








    fun isClassicSystemBarsWindow(activity: Activity): Boolean =
        isClassicKeyboardResizeWindow(activity.window)

    private fun applyKeyboardMode(
        activity: Activity,
        keyboardAdjustMode: KeyboardAdjustMode?,
        tag: String,
        decorFitsSystemWindows: Boolean
    ) {
        val contentView = activity.findViewById<View>(android.R.id.content) ?: return

        val mode = keyboardAdjustMode ?: KeyboardAdjustMode.RESIZE

        when (mode) {
            KeyboardAdjustMode.RESIZE -> {

                
                
                
                
                val useManualImePadding = !decorFitsSystemWindows &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

                if (!useManualImePadding) {
                    @Suppress("DEPRECATION")
                    activity.window.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                    )
                    clearImePadding(contentView)
                    AppLogger.d(tag, "键盘模式: RESIZE (系统调整)")
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                    )
                    installManualImePadding(contentView)
                    AppLogger.d(tag, "键盘模式: RESIZE (边到边，只滚动被挡住的内容并裁到键盘上沿，结束时不改高度)")
                }
            }

            KeyboardAdjustMode.NOTHING -> {

                @Suppress("DEPRECATION")
                activity.window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                )

                clearImePadding(contentView)

                AppLogger.d(tag, "键盘模式: NOTHING (覆盖)")
            }
        }
    }

    private fun installManualImePadding(contentView: View) {
        if (contentView in manualImeInstalled) {
            ViewCompat.requestApplyInsets(contentView)
            return
        }
        manualImeInstalled.add(contentView)

        var imeAnimating = false
        val follow = ImeAnimationFollow()
        val documentScroll = ImeDocumentScroll()

        fun applyFrame(frame: ImeFrame) {
            
            
            
            
            if (contentView.translationY != 0f) contentView.translationY = 0f
            if (contentView.paddingBottom != 0) {
                contentView.setPadding(
                    contentView.paddingLeft,
                    contentView.paddingTop,
                    contentView.paddingRight,
                    0
                )
                publishImeOcclusion(contentView, 0)
            }
            val clip = frame.clipBottom
            if (clip != null && clip > 0 && contentView.height > 0 && contentView.width > 0) {
                val clipBottom = (contentView.height - clip).coerceAtLeast(0)
                contentView.clipBounds = Rect(0, 0, contentView.width, clipBottom)
            } else if (clip == 0 && contentView.clipBounds != null) {
                contentView.clipBounds = null
            }
        }

        var latestImeBottom = 0

        fun beginDocumentScroll() {
            documentScroll.begin()
        }

        fun applyDocumentScroll(webView: android.webkit.WebView, imeBottom: Int) {
            if (imeBottom <= 0 || webView.height <= 0) return
            val web = IntArray(2)
            val content = IntArray(2)
            webView.getLocationInWindow(web)
            contentView.getLocationInWindow(content)
            val overlap = keyboardOverlapPx(
                web[1], webView.height, content[1], contentView.height, imeBottom,
            )
            val delta = documentScroll.scrollDelta(webView.height, overlap)
            if (delta > 0) webView.scrollBy(0, delta)
        }

        fun scrollCoveredField(imeBottom: Int) {
            latestImeBottom = imeBottom
            val webView = findWebViewInHierarchy(contentView) ?: return
            if (webView.height <= 0 || imeBottom <= 0) return
            val generation = documentScroll.requestSnapshot()
            if (generation != null) {
                val heightPx = webView.height
                webView.evaluateJavascript(FOCUSED_FIELD_SNAPSHOT_JS) { raw ->
                    val snap = parseFocusedFieldSnapshot(raw)
                    if (snap == null) {
                        documentScroll.snapshotMissed(generation)
                        return@evaluateJavascript
                    }
                    documentScroll.capture(
                        snap.topCss,
                        snap.bottomCss,
                        snap.viewportHeightCss,
                        heightPx,
                        generation,
                    )
                    
                    
                    webView.post {
                        if (webView.isAttachedToWindow) applyDocumentScroll(webView, latestImeBottom)
                    }
                }
            }
            applyDocumentScroll(webView, imeBottom)
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            contentView,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        imeAnimating = true
                        follow.onPrepare()
                        beginDocumentScroll()
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (runningAnimations.any { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }) {
                        if (!imeAnimating) {
                            imeAnimating = true
                            follow.onPrepare()
                            beginDocumentScroll()
                        }
                        val shown = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        val frame = follow.onFrame(shown)
                        applyFrame(frame)
                        if (frame.scrollDocument) scrollCoveredField(shown)
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    imeAnimating = false

                    val rootInsets = ViewCompat.getRootWindowInsets(contentView)
                    val imeVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
                    val imeBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    val finalBottom = if (imeVisible) imeBottom else 0
                    
                    
                    
                    
                    if (finalBottom > follow.settledBottom) scrollCoveredField(finalBottom)
                    documentScroll.end()
                    applyFrame(follow.onEnd(finalBottom))
                }
            }
        )

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeAnimating) {
                
                
                follow.onTarget(imeBottom)
            } else {
                applyFrame(follow.jumpTo(imeBottom))
            }
            windowInsets
        }

        ViewCompat.requestApplyInsets(contentView)
    }

    private fun clearImePadding(contentView: View) {
        manualImeInstalled.remove(contentView)
        ViewCompat.setWindowInsetsAnimationCallback(contentView, null)
        ViewCompat.setOnApplyWindowInsetsListener(contentView, null)
        contentView.translationY = 0f
        contentView.clipBounds = null
        if (contentView.paddingBottom != 0) {
            contentView.setPadding(
                contentView.paddingLeft,
                contentView.paddingTop,
                contentView.paddingRight,
                0
            )
        }
        publishImeOcclusion(contentView, 0)
    }

    private fun findWebViewInHierarchy(view: View): android.webkit.WebView? {
        if (view is android.webkit.WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findWebViewInHierarchy(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    fun showCustomView(
        activity: Activity,
        view: View
    ): Int {
        val originalOrientation = activity.requestedOrientation

        
        
        
        
        
        if (view.parent == null) {
            val decorView = activity.window.decorView as FrameLayout
            decorView.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        return originalOrientation
    }

    private const val FULLSCREEN_VIDEO_DETECT_JS = """
        (function() {
          try {
            var fe = document.fullscreenElement
              || document.webkitFullscreenElement
              || document.webkitCurrentFullScreenElement;
            if (fe) {
              if (fe.tagName === 'VIDEO') return 'video';
              if (fe.querySelector && fe.querySelector('video')) return 'video';
              return 'other';
            }
            var vids = document.getElementsByTagName('video');
            for (var i = 0; i < vids.length; i++) {
              var v = vids[i];
              if (!v.paused && !v.ended && v.readyState > 2) return 'video';
            }
            return 'none';
          } catch (e) {
            return 'none';
          }
        })();
    """

    fun applyFullscreenVideoOrientation(
        activity: Activity,
        webView: android.webkit.WebView?,
        fullscreenOrientation: com.webtoapp.data.model.FullscreenVideoOrientation =
            com.webtoapp.data.model.FullscreenVideoOrientation.AUTO_SENSOR_LANDSCAPE
    ) {
        val targetOrientation = when (fullscreenOrientation) {
            com.webtoapp.data.model.FullscreenVideoOrientation.AUTO_SENSOR_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            com.webtoapp.data.model.FullscreenVideoOrientation.FORCE_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            com.webtoapp.data.model.FullscreenVideoOrientation.KEEP_CURRENT -> {
                AppLogger.d("WindowHelper", "Fullscreen: keeping current orientation")
                return
            }
        }

        if (webView == null) {
            activity.requestedOrientation = targetOrientation
            AppLogger.d("WindowHelper", "Fullscreen orientation applied without detection (no WebView)")
            return
        }

        webView.evaluateJavascript(FULLSCREEN_VIDEO_DETECT_JS) { result ->
            val isVideo = result != null && result.contains("video")
            if (isVideo) {
                activity.requestedOrientation = targetOrientation
                AppLogger.d("WindowHelper", "Fullscreen video detected, orientation -> $targetOrientation")
            } else {
                AppLogger.d("WindowHelper", "Fullscreen content is not a video ($result), orientation kept")
            }
        }
    }

    fun hideCustomView(
        activity: Activity,
        view: View,
        callback: WebChromeClient.CustomViewCallback?,
        originalOrientation: Int
    ) {
        
        
        
        val decorView = activity.window.decorView as FrameLayout
        if (view.parent === decorView) {
            decorView.removeView(view)
        }
        callback?.onCustomViewHidden()
        activity.requestedOrientation = originalOrientation
    }
}
