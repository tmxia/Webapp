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
