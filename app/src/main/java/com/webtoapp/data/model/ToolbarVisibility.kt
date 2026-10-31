package com.webtoapp.data.model










data class ToolbarButtonVisibility(
    val showTitle: Boolean,
    val showUrl: Boolean,
    val showBack: Boolean,
    val showForward: Boolean,
    val showRefresh: Boolean,
    val showConsoleButton: Boolean,
    val showFind: Boolean
)










fun hasAnyToolbarItem(
    toolbarShowTitle: Boolean,
    toolbarShowUrl: Boolean,
    toolbarShowBack: Boolean,
    toolbarShowForward: Boolean,
    toolbarShowRefresh: Boolean,
    toolbarShowConsole: Boolean,
    toolbarShowFind: Boolean
): Boolean = toolbarShowTitle || toolbarShowUrl || toolbarShowBack || toolbarShowForward ||
    toolbarShowRefresh || toolbarShowConsole || toolbarShowFind










fun isBrowserToolbarVisible(toolbarEnabled: Boolean, hasAnyItem: Boolean): Boolean =
    toolbarEnabled && hasAnyItem





fun drawerSiteBarVisible(browserToolbarVisible: Boolean): Boolean = !browserToolbarVisible


fun String.shortenForBrowserChrome(): String {
    val withoutScheme = when {
        startsWith("https://") -> substring(8)
        startsWith("http://") -> substring(7)
        else -> this
    }
    return withoutScheme.substringBefore('?').substringBefore('#')
}






fun resolveToolbarButtons(
    toolbarEnabled: Boolean,
    toolbarShowTitle: Boolean,
    toolbarShowUrl: Boolean,
    toolbarShowBack: Boolean,
    toolbarShowForward: Boolean,
    toolbarShowRefresh: Boolean,
    toolbarShowConsole: Boolean,
    toolbarShowFind: Boolean
): ToolbarButtonVisibility = ToolbarButtonVisibility(
    showTitle = toolbarEnabled && toolbarShowTitle,
    showUrl = toolbarEnabled && toolbarShowUrl,
    showBack = toolbarEnabled && toolbarShowBack,
    showForward = toolbarEnabled && toolbarShowForward,
    showRefresh = toolbarEnabled && toolbarShowRefresh,
    showConsoleButton = toolbarEnabled && toolbarShowConsole,
    showFind = toolbarEnabled && toolbarShowFind
)
