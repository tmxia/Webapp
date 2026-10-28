package com.webtoapp.core.webview

import android.view.InputDevice
import android.view.MotionEvent



















class MouseInputCompat {

    
    private var realPrimaryDown = false

    
    private var synthesizedPrimaryDown = false

    




    fun noteTouchEvent(source: Int, toolType: Int, actionMasked: Int) {
        if (!isMouse(source, toolType)) return
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                
                
                if (!synthesizedPrimaryDown) realPrimaryDown = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                realPrimaryDown = false
                synthesizedPrimaryDown = false
            }
        }
    }

    




    fun translateButtonAction(
        source: Int,
        toolType: Int,
        actionMasked: Int,
        actionButton: Int
    ): Int? {
        if (!isMouse(source, toolType)) return null
        if (actionButton != MotionEvent.BUTTON_PRIMARY) return null
        return when (actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS ->
                if (realPrimaryDown || synthesizedPrimaryDown) {
                    
                    
                    null
                } else {
                    synthesizedPrimaryDown = true
                    MotionEvent.ACTION_DOWN
                }
            MotionEvent.ACTION_BUTTON_RELEASE ->
                if (synthesizedPrimaryDown) {
                    synthesizedPrimaryDown = false
                    MotionEvent.ACTION_UP
                } else {
                    null
                }
            else -> null
        }
    }

    companion object {

        fun isMouse(source: Int, toolType: Int): Boolean =
            (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                toolType == MotionEvent.TOOL_TYPE_MOUSE

        




        fun copyWithAction(event: MotionEvent, newAction: Int): MotionEvent {
            val count = event.pointerCount
            val properties = Array(count) { i ->
                MotionEvent.PointerProperties().also { event.getPointerProperties(i, it) }
            }
            val coords = Array(count) { i ->
                MotionEvent.PointerCoords().also { event.getPointerCoords(i, it) }
            }
            return MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                newAction,
                count,
                properties,
                coords,
                event.metaState,
                event.buttonState,
                event.xPrecision,
                event.yPrecision,
                event.deviceId,
                event.edgeFlags,
                event.source,
                event.flags
            )
        }
    }
}
