package com.webtoapp.core.plugin










fun resolveAttachedPluginIds(
    pluginsEnabled: Boolean,
    attachedIds: List<String>,
    globalEnabledIds: List<String>,
): List<String> {
    if (!pluginsEnabled) return emptyList()
    val explicit = attachedIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (explicit.isNotEmpty()) return explicit
    return globalEnabledIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}


internal fun updateGlobalEnabledIds(current: List<String>, id: String, enabled: Boolean): List<String> {
    val clean = id.trim()
    if (clean.isEmpty()) return current
    return if (enabled) {
        if (clean in current) current else current + clean
    } else {
        current.filterNot { it == clean }
    }
}
