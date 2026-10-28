package com.webtoapp.core.i18n

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking









object HostLanguage {

    private const val TAG = "HostLanguage"

    fun apply(context: Context, language: AppLanguage) {
        Strings.setLanguage(language)
        Strings.attachContext(context, language)
    }

    



    fun applyBlocking(context: Context) {
        try {
            val language = runBlocking(Dispatchers.IO) {
                LanguageManager.getInstance(context).getCurrentLanguage()
            }
            apply(context, language)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply saved language", e)
        }
    }
}
