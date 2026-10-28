package com.webtoapp.core.category

import android.content.Context












class CategoryFilterStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rememberEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    
    fun loadSelection(): Long? {
        if (!prefs.contains(KEY_SELECTED)) return null
        return when (val v = prefs.getLong(KEY_SELECTED, VALUE_ALL)) {
            VALUE_ALL -> null
            else -> v
        }
    }

    fun saveSelection(categoryId: Long?) {
        prefs.edit().putLong(KEY_SELECTED, categoryId ?: VALUE_ALL).apply()
    }

    private companion object {
        const val PREFS_NAME = "home_category_filter"
        const val KEY_REMEMBER = "remember_enabled"
        const val KEY_SELECTED = "selected_category"
        const val VALUE_ALL = Long.MIN_VALUE
    }
}
