package com.webtoapp.ui.animation

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.webtoapp.R
import com.webtoapp.ui.design.WtaMotion














object HostPreviewMotion {
    const val EXTRA = "wta_host_preview_motion"

    fun launch(context: Context, intent: Intent) {
        intent.putExtra(EXTRA, true)
        val options = if (shouldAnimate()) {
            ActivityOptions.makeCustomAnimation(
                context,
                R.anim.host_preview_open_enter,
                R.anim.host_preview_open_exit,
            )
        } else {
            ActivityOptions.makeCustomAnimation(context, 0, 0)
        }
        context.startActivity(intent, options.toBundle())
    }

    
    fun install(activity: Activity) {
        if (!activity.intent.getBooleanExtra(EXTRA, false)) return
        if (Build.VERSION.SDK_INT < 34) return
        val (enter, exit) = closeAnims()
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enter, exit)
    }

    
    fun onFinish(activity: Activity) {
        if (!activity.intent.getBooleanExtra(EXTRA, false)) return
        if (Build.VERSION.SDK_INT >= 34) return
        val (enter, exit) = closeAnims()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(enter, exit)
    }

    private fun shouldAnimate(): Boolean =
        WtaMotion.motionEnabled && WtaMotion.durationScale > 0.35f

    private fun closeAnims(): Pair<Int, Int> =
        if (shouldAnimate()) {
            R.anim.host_preview_close_enter to R.anim.host_preview_close_exit
        } else {
            0 to 0
        }
}
