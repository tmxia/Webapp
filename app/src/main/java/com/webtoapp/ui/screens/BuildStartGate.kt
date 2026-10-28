package com.webtoapp.ui.screens









internal enum class BuildStartAction {
    Start,
    Remember,
    IgnoreAlreadyBuilding,
}

internal fun buildStartAction(
    isBuilding: Boolean,
    builderReady: Boolean,
    runtimeEnsureInProgress: Boolean,
): BuildStartAction = when {
    isBuilding -> BuildStartAction.IgnoreAlreadyBuilding
    !builderReady || runtimeEnsureInProgress -> BuildStartAction.Remember
    else -> BuildStartAction.Start
}


internal enum class ArmedBuild {
    Wait,
    Start,
    Fail,
}

internal fun armedBuildStep(
    builderReady: Boolean,
    runtimeEnsureInProgress: Boolean,
    builderFailed: Boolean,
): ArmedBuild = when {
    builderReady && !runtimeEnsureInProgress -> ArmedBuild.Start
    builderFailed && !builderReady -> ArmedBuild.Fail
    else -> ArmedBuild.Wait
}
