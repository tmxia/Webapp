package com.webtoapp.core.ads.api

import android.app.Activity
import android.content.Context
import android.view.View















interface AdsFeatureApi {

    
    fun initialize(context: Context, appId: String, testMode: Boolean)

    
    fun createBanner(context: Context, adUnitId: String): View?

    
    fun showInterstitial(activity: Activity, adUnitId: String, onDone: AdsCompletion)

    

    fun showSplashAd(
        activity: Activity,
        adUnitId: String,
        timeoutSeconds: Int,
        onDone: AdsCompletion
    )

    
    fun destroy()

    companion object {
        
        const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_SPLASH = "ca-app-pub-3940256099942544/9257395921"
    }
}










fun interface AdsCompletion {
    fun onDone()
}
