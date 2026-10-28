package com.webtoapp.core.activation






data class RemainingDaysNotice(
    val days: Long,
    val hours: Long,
)


fun ActivationCodeType.offersRemainingDays(): Boolean =
    this == ActivationCodeType.TIME_LIMITED || this == ActivationCodeType.COMBINED

private const val HOUR_MS = 60L * 60L * 1000L
private const val DAY_MS = 24L * HOUR_MS






fun remainingDaysNotice(
    status: ActivationStatus,
    code: ActivationCode?,
    nowMs: Long = System.currentTimeMillis(),
): RemainingDaysNotice? {
    if (code == null || !code.showRemainingDays) return null
    if (!code.type.offersRemainingDays()) return null
    if (!status.isActivated) return null
    val usageLimit = status.usageLimit
    if (usageLimit != null && status.usageCount >= usageLimit) return null
    val expire = status.expireTime ?: return null
    val remaining = expire - nowMs
    if (remaining <= 0L) return null
    val days = remaining / DAY_MS
    var hours = (remaining % DAY_MS) / HOUR_MS
    if (days == 0L && hours == 0L) hours = 1L
    return RemainingDaysNotice(days, hours)
}
