package com.webtoapp.core.webview











object OAuthProviderHosts {

    val HOST_SUFFIXES = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",

        "www.facebook.com",
        "m.facebook.com",

        "appleid.apple.com",

        "login.microsoftonline.com",
        "login.live.com",

        "github.com",

        "api.twitter.com",
        "api.x.com"
    )

    fun isOAuthHost(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }

    fun extractHost(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        var rest = url.substring(schemeEnd + 3)
        rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        rest = rest.substringAfterLast('@')
        return rest.substringBefore(':').lowercase().ifEmpty { null }
    }
}
