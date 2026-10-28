package com.webtoapp.ui.shared






fun splashCornerChipVisible(
    showCountdown: Boolean,
    displayTimeSeconds: Int,
    skipAvailable: Boolean,
): Boolean = (showCountdown && displayTimeSeconds > 0) || skipAvailable
