package com.webtoapp.ui.shared














internal class ImeAnimationFollow {
    
    var settledBottom: Int = 0
        private set

    private var animating = false
    private var targetBottom = 0

    fun onPrepare() {
        animating = true
        targetBottom = settledBottom
    }

    
    fun onTarget(bottom: Int) {
        if (!animating) return
        targetBottom = bottom.coerceAtLeast(0)
    }

    fun onFrame(shown: Int): ImeFrame {
        val visible = shown.coerceAtLeast(0)
        val closing = targetBottom < settledBottom ||
            (targetBottom == settledBottom && visible < settledBottom)
        return if (closing) {
            ImeFrame(translationY = 0f, layoutBottom = null, scrollDocument = false, clipBottom = visible)
        } else {
            ImeFrame(translationY = 0f, layoutBottom = null, scrollDocument = true, clipBottom = visible)
        }
    }

    fun onEnd(finalBottom: Int): ImeFrame = settle(finalBottom)

    fun jumpTo(bottom: Int): ImeFrame = settle(bottom)

    private fun settle(bottom: Int): ImeFrame {
        animating = false
        val settled = bottom.coerceAtLeast(0)
        settledBottom = settled
        targetBottom = settled
        return ImeFrame(translationY = 0f, layoutBottom = null, scrollDocument = false, clipBottom = settled)
    }
}

internal data class ImeFrame(
    val translationY: Float,
    val layoutBottom: Int?,
    val scrollDocument: Boolean = false,
    
    val clipBottom: Int? = null,
)
