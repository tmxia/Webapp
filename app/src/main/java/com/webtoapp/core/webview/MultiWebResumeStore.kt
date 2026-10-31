package com.webtoapp.core.webview

import android.content.Context











class MultiWebResumeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    




    fun persistSelectedSiteId(key: String, siteId: String?) {
        val prefKey = key + SUFFIX_SITE
        if (siteId.isNullOrBlank()) {
            prefs.edit().remove(prefKey).apply()
        } else {
            prefs.edit().putString(prefKey, siteId).apply()
        }
    }

    




    fun resumeSiteId(key: String): String? = prefs.getString(key + SUFFIX_SITE, null)

    
    fun savedSiteIds(key: String): Set<String> {
        val raw = prefs.getStringSet(key + SUFFIX_SAVED, null) ?: return emptySet()
        return raw.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun persistSavedSiteIds(key: String, siteIds: Set<String>) {
        val clean = siteIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val prefKey = key + SUFFIX_SAVED
        if (clean.isEmpty()) {
            prefs.edit().remove(prefKey).apply()
        } else {
            prefs.edit().putStringSet(prefKey, clean).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "multiweb_resume"
        const val SUFFIX_SITE = ".site"
        const val SUFFIX_SAVED = ".saved"
    }
}


internal fun multiWebSiteMatchesQuery(name: String, url: String, domain: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return name.contains(q, ignoreCase = true) ||
        domain.contains(q, ignoreCase = true) ||
        url.contains(q, ignoreCase = true)
}
