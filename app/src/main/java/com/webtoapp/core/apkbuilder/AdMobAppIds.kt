package com.webtoapp.core.apkbuilder






internal object AdMobAppIds {
    private val PATTERN = Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$")

    
    const val SAMPLE = "ca-app-pub-3940256099942544~3347511713"

    fun isValid(id: String): Boolean = PATTERN.matches(id.trim())

    




    fun manifestValue(configured: String, testMode: Boolean): String? {
        val trimmed = configured.trim()
        if (isValid(trimmed)) return trimmed
        if (testMode) return SAMPLE
        return null
    }
}
