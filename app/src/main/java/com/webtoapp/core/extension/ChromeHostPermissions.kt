package com.webtoapp.core.extension











object ChromeHostPermissions {

    




    fun declaredPatterns(manifestJson: String): List<String> = runCatching {
        val manifest = org.json.JSONObject(manifestJson)
        val out = mutableListOf<String>()
        listOf("host_permissions", "permissions", "optional_permissions").forEach { key ->
            manifest.optJSONArray(key)?.let { arr ->
                for (i in 0 until arr.length()) {
                    val entry = arr.optString(i) ?: continue
                    if (entry == "<all_urls>" || entry.contains("://")) out.add(entry)
                }
            }
        }
        out
    }.getOrDefault(emptyList())

    





    fun matches(pattern: String, url: String): Boolean {
        if (pattern == "<all_urls>") return true

        val pParts = splitPattern(pattern) ?: return false
        val uParts = splitPattern(url) ?: return false

        if (pParts.scheme != "*" && pParts.scheme != uParts.scheme) return false
        if (!hostMatches(pParts.host, uParts.host)) return false
        if (!portMatches(pParts.port, pParts.scheme, uParts.scheme, uParts.port)) return false
        return pathMatches(pParts.path, uParts.path)
    }

    



    data class ContentScriptRule(
        val matches: List<String>,
        val excludeMatches: List<String> = emptyList(),
    )

    
    data class ExtensionAuth(
        val id: String,
        val hostPatterns: List<String>,
        val contentScripts: List<ContentScriptRule>,
    )

    fun contentScriptRules(manifestJson: String): List<ContentScriptRule> = runCatching {
        val scripts = org.json.JSONObject(manifestJson).optJSONArray("content_scripts") ?: return emptyList()
        buildList {
            for (i in 0 until scripts.length()) {
                val entry = scripts.optJSONObject(i) ?: continue
                add(
                    ContentScriptRule(
                        matches = jsonStrings(entry.optJSONArray("matches")),
                        excludeMatches = jsonStrings(entry.optJSONArray("exclude_matches")),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    




    fun authorizes(auth: ExtensionAuth, callerUrl: String, targetUrl: String): Boolean {
        if (callerUrl.isBlank() || targetUrl.isBlank()) return false
        val ownPage = isExtensionOrigin(auth.id, callerUrl)
        val contentScript = auth.contentScripts.any { rule ->
            rule.matches.any { matches(it, callerUrl) } &&
                rule.excludeMatches.none { matches(it, callerUrl) }
        }
        if (!ownPage && !contentScript) return false
        return auth.hostPatterns.any { matches(it, targetUrl) }
    }

    fun anyAuthorized(auths: List<ExtensionAuth>, callerUrl: String, targetUrl: String): Boolean =
        auths.any { authorizes(it, callerUrl, targetUrl) }

    fun isExtensionOrigin(extensionId: String, callerUrl: String): Boolean {
        val prefix = "chrome-extension://$extensionId"
        val normalized = callerUrl.trim()
        return normalized.equals(prefix, ignoreCase = true) ||
            normalized.startsWith("$prefix/", ignoreCase = true)
    }

    
    fun resolveRedirect(currentUrl: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return runCatching { java.net.URL(java.net.URL(currentUrl), location.trim()).toString() }.getOrNull()
    }

    fun isHttpUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme == "http" || scheme == "https"
    }

    private fun jsonStrings(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (!value.isNullOrEmpty()) add(value)
            }
        }
    }

    




    private fun portMatches(
        patternPort: String,
        patternScheme: String,
        urlScheme: String,
        urlPort: String,
    ): Boolean {
        if (patternPort == "*") return true
        val actual = if (urlPort.isEmpty()) defaultPort(urlScheme) else urlPort
        if (patternPort.isEmpty()) {
            val scheme = if (patternScheme == "*") urlScheme else patternScheme
            return actual == defaultPort(scheme)
        }
        return patternPort == actual
    }

    private fun defaultPort(scheme: String): String = when (scheme.lowercase()) {
        "http", "ws" -> "80"
        "https", "wss" -> "443"
        "ftp" -> "21"
        else -> ""
    }

    private data class UrlParts(val scheme: String, val host: String, val port: String, val path: String)

    private fun splitPattern(raw: String): UrlParts? {
        val sep = raw.indexOf("://")
        if (sep <= 0) return null
        val scheme = raw.substring(0, sep).lowercase()
        val rest = raw.substring(sep + 3)
        if (rest.isEmpty()) return null

        val pathStart = rest.indexOf('/')
        val authority = if (pathStart >= 0) rest.substring(0, pathStart) else rest
        val path = if (pathStart >= 0) rest.substring(pathStart) else "/"

        
        val host: String
        val port: String
        
        
        val bracket = authority.lastIndexOf(']')
        val colon = authority.lastIndexOf(':')
        val portColon = if (colon > bracket) colon else -1
        if (portColon == -1) {
            host = authority
            port = ""
        } else {
            host = authority.substring(0, portColon)
            port = authority.substring(portColon + 1)
        }
        if (host.isEmpty()) return null
        return UrlParts(scheme, host.lowercase(), port, path)
    }

    private fun hostMatches(patternHost: String, host: String): Boolean {
        if (patternHost == "*" || patternHost == host) return true
        if (patternHost.startsWith("*.")) {
            val suffix = patternHost.substring(1) 
            if (host.endsWith(suffix)) return true
            
            return host == suffix.removePrefix(".")
        }
        return false
    }

    private fun pathMatches(patternPath: String, path: String): Boolean {
        if (patternPath == "/*" || patternPath == "*" || patternPath == path) return true
        if (!patternPath.contains('*')) return false
        val regex = buildString {
            append('^')
            patternPath.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    else -> {
                        if ("\\.[]{}()+?^$|".contains(ch)) append('\\')
                        append(ch)
                    }
                }
            }
            append('$')
        }
        return runCatching { Regex(regex).matches(path) }.getOrDefault(false)
    }

}
