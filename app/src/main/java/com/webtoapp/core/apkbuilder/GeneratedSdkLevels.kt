package com.webtoapp.core.apkbuilder









object GeneratedSdkLevels {
    const val TEMPLATE_MIN_SDK = 23
    const val TEMPLATE_TARGET_SDK = 35
    const val COMPILE_SDK_CAP = 36
    const val PROCESS_EXEC_TARGET_SDK = 28

    
    const val DEFAULT_RAISED_MIN_SDK = 24

    
    val TARGET_SDK_CHOICES = listOf(28, 31, 33, 34, 35, 36)

    
    val MIN_SDK_CHOICES = listOf(23, 24, 26, 28, 31, 33, 34, 35, 36)

    data class Resolved(
        val minSdk: Int,
        val targetSdk: Int,
        
        val targetSdkRewrite: Int?,
        
        val minSdkRewrite: Int?
    )

    fun resolve(
        requiresProcessExec: Boolean,
        minSdkOverride: Int?,
        targetSdkOverride: Int?
    ): Resolved {
        val targetRaw = if (requiresProcessExec) {
            PROCESS_EXEC_TARGET_SDK
        } else {
            targetSdkOverride?.takeIf { it > 0 }
        }
        val target = (targetRaw ?: TEMPLATE_TARGET_SDK)
            .coerceIn(TEMPLATE_MIN_SDK, COMPILE_SDK_CAP)

        val minRequested = minSdkOverride?.takeIf { it > 0 }
        val min = (minRequested ?: TEMPLATE_MIN_SDK)
            .coerceIn(TEMPLATE_MIN_SDK, target)

        val targetRewrite = when {
            requiresProcessExec -> PROCESS_EXEC_TARGET_SDK
            targetSdkOverride != null && targetSdkOverride > 0 -> target
            else -> null
        }
        val minRewrite = if (minRequested != null) min else null
        return Resolved(
            minSdk = min,
            targetSdk = target,
            targetSdkRewrite = targetRewrite,
            minSdkRewrite = minRewrite
        )
    }

    fun defaultRaisedMinSdk(effectiveTargetSdk: Int): Int =
        minOf(DEFAULT_RAISED_MIN_SDK, effectiveTargetSdk).coerceAtLeast(TEMPLATE_MIN_SDK)

    fun clampMinSdk(minSdk: Int?, effectiveTargetSdk: Int): Int? {
        val requested = minSdk?.takeIf { it > 0 } ?: return minSdk
        return requested.coerceIn(TEMPLATE_MIN_SDK, effectiveTargetSdk)
    }
}
