package com.webtoapp.core.plugin

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.webtoapp.util.GsonProvider
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException





























const val PAGE_SCRIPT_TYPE = "hcj/page"

private val PAGE_SCRIPT_RE = Regex(
    """<script[^>]*type\s*=\s*["']hcj/page["'][^>]*>(.*?)</script>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
private val HTML_COMMENT_RE = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
private val BODY_RE = Regex(
    """<body[^>]*>(.*?)</body>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
private val SCRIPT_CLOSE_RE = Regex("</script", RegexOption.IGNORE_CASE)


fun extractPageJs(pluginHtml: String): String =
    PAGE_SCRIPT_RE.findAll(pluginHtml)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")






fun hasPanelMarkup(pluginHtml: String): Boolean {
    if (pluginHtml.isBlank()) return false
    val region = BODY_RE.find(pluginHtml)?.groupValues?.get(1) ?: pluginHtml
    return region
        .replace(PAGE_SCRIPT_RE, "")
        .replace(HTML_COMMENT_RE, "")
        .isNotBlank()
}








fun buildPluginHtml(pageJs: String, panelDoc: String): String = buildString {
    if (pageJs.isNotBlank()) {
        append("<script type=\"").append(PAGE_SCRIPT_TYPE).append("\">\n")
        append(pageJs.trim().replace(SCRIPT_CLOSE_RE, "<\\/script"))
        append("\n</script>\n")
    }
    if (panelDoc.isNotBlank()) {
        if (isNotEmpty()) append("\n")
        append(panelDoc.trim()).append("\n")
    }
}





enum class PluginKind {
    HCJ,
    USERSCRIPT,
    CHROME_EXTENSION;

    companion object {
        fun parse(raw: String?): PluginKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HCJ
    }
}

enum class PluginRunAt(val jsEvent: String) {
    DOCUMENT_START(""),
    DOCUMENT_END("DOMContentLoaded"),
    DOCUMENT_IDLE("load");

    companion object {
        fun parse(raw: String?): PluginRunAt =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: DOCUMENT_END
    }
}





enum class PluginEntryStyle {
    
    TOOLBAR,
    
    FLOATING_HANDLE,
    
    MENU;

    companion object {
        fun parse(raw: String?): PluginEntryStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TOOLBAR
    }
}


enum class PluginPanelStyle {
    
    BOTTOM_SHEET,
    
    FLOATING_WINDOW,
    
    FULLSCREEN;

    companion object {
        fun parse(raw: String?): PluginPanelStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BOTTOM_SHEET
    }
}





enum class PluginPermission {
    
    STORAGE,
    
    FETCH,
    
    NOTIFY,
    
    BADGE,
    
    CLIPBOARD,
    
    DOWNLOAD,
    
    SHARE,
    
    OPEN_EXTERNAL,
    
    CLEAR_DATA,
    
    EXIT_APP;

    companion object {
        fun parse(raw: String?): PluginPermission? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}





data class PluginMatchRule(
    @SerializedName("pattern")
    val pattern: String,
    @SerializedName("exclude")
    val exclude: Boolean = false,
    @SerializedName("isRegex")
    val isRegex: Boolean = false
)

private const val REGEX_TIMEOUT_MS = 200L

private val regexExecutor by lazy {
    Executors.newSingleThreadExecutor { r ->
        Thread(r, "PluginSafeRegex").apply { isDaemon = true }
    }
}

private val regexCache = object : LinkedHashMap<String, Regex>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?) = size > 64
}

internal fun pluginSafeRegexMatch(pattern: String, input: String): Boolean {
    val future = try {
        val compiledRegex = synchronized(regexCache) {
            regexCache.getOrPut(pattern) { Regex(pattern) }
        }
        regexExecutor.submit<Boolean> { compiledRegex.containsMatchIn(input) }
    } catch (e: Exception) {
        return false
    }
    return try {
        future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        
        
        future.cancel(true)
        false
    } catch (e: Exception) {
        false
    }
}

internal fun pluginGlobMatches(url: String, rule: PluginMatchRule): Boolean {
    if (rule.isRegex) return pluginSafeRegexMatch(rule.pattern, url)
    val pattern = rule.pattern
    if (pattern == "*" || pattern == "<all_urls>") return true

    val regexPattern = buildString {
        append("^")
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '*' && pattern.startsWith("*://", i) -> {
                    append("(https?|ftp|file)://")
                    i += 4
                }
                c == '*' -> {
                    append(".*")
                    i++
                }
                c in ".+?^\${}()|[]\\/" -> {
                    append("\\")
                    append(c)
                    i++
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
        append("$")
    }
    return try {
        synchronized(regexCache) {
            regexCache.getOrPut("i:$regexPattern") {
                Regex(regexPattern, setOf(RegexOption.IGNORE_CASE))
            }
        }.matches(url)
    } catch (e: Exception) {
        url.contains(pattern, ignoreCase = true)
    }
}










data class PluginManifest(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String,
    @SerializedName("version")
    val version: String = "1.0.0",
    @SerializedName("description")
    val description: String = "",
    @SerializedName("author")
    val author: String = "",
    @SerializedName("homepage")
    val homepage: String = "",
    
    @SerializedName("icon")
    val icon: String = "extension",
    @SerializedName("matches")
    val matches: List<String> = listOf("*"),
    @SerializedName("excludeMatches")
    val excludeMatches: List<String> = emptyList(),
    @SerializedName("runAt")
    val runAt: String = "document_end",
    @SerializedName("permissions")
    val permissions: List<String> = emptyList(),
    
    @SerializedName("toolbar")
    val toolbar: Boolean = true,
    
    @SerializedName("preferredEntry")
    val preferredEntry: String = "",
    
    @SerializedName("preferredPanel")
    val preferredPanel: String = "",
    
    @SerializedName("gmGrants")
    val gmGrants: List<String> = emptyList(),
    
    @SerializedName("requireUrls")
    val requireUrls: List<String> = emptyList(),
    
    @SerializedName("resources")
    val resources: Map<String, String> = emptyMap(),
    @SerializedName("noframes")
    val noframes: Boolean = false,
    





    @SerializedName("legacyCompat")
    val legacyCompat: Boolean = false
) {
    fun resolvedId(fallback: String): String = id.takeIf { it.isNotBlank() } ?: fallback

    fun resolvedRunAt(): PluginRunAt = PluginRunAt.parse(runAt)

    fun resolvedPermissions(): Set<PluginPermission> =
        permissions.mapNotNull { PluginPermission.parse(it) }.toSet()

    fun matchRules(): List<PluginMatchRule> =
        matches.map { toRule(it, exclude = false) } +
            excludeMatches.map { toRule(it, exclude = true) }

    
    private fun toRule(raw: String, exclude: Boolean): PluginMatchRule {
        val t = raw.trim()
        return if (t.length > 2 && t.startsWith("/") && t.endsWith("/")) {
            PluginMatchRule(pattern = t.substring(1, t.length - 1), exclude = exclude, isRegex = true)
        } else {
            PluginMatchRule(pattern = t, exclude = exclude)
        }
    }

    companion object {
        private val gson get() = GsonProvider.gson

        private fun str(obj: JsonObject, key: String, def: String): String =
            obj.get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString ?: def

        private fun bool(obj: JsonObject, key: String, def: Boolean): Boolean =
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: def

        private fun strList(obj: JsonObject, key: String, def: List<String>): List<String> =
            obj.getAsJsonArray(key)
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: def

        private fun strMap(obj: JsonObject, key: String): Map<String, String> =
            obj.getAsJsonObject(key)?.entrySet()
                ?.mapNotNull { (k, v) ->
                    v.takeIf { it.isJsonPrimitive }?.asString?.let { k to it }
                }?.toMap() ?: emptyMap()

        




        fun fromJson(json: String): PluginManifest? = try {
            val obj = JsonParser.parseString(json).asJsonObject
            PluginManifest(
                id = str(obj, "id", ""),
                name = str(obj, "name", ""),
                version = str(obj, "version", "1.0.0"),
                description = str(obj, "description", ""),
                author = str(obj, "author", ""),
                homepage = str(obj, "homepage", ""),
                icon = str(obj, "icon", "extension"),
                matches = strList(obj, "matches", listOf("*")),
                excludeMatches = strList(obj, "excludeMatches", emptyList()),
                runAt = str(obj, "runAt", "document_end"),
                permissions = strList(obj, "permissions", emptyList()),
                toolbar = bool(obj, "toolbar", true),
                preferredEntry = str(obj, "preferredEntry", ""),
                preferredPanel = str(obj, "preferredPanel", ""),
                gmGrants = strList(obj, "gmGrants", emptyList()),
                requireUrls = strList(obj, "requireUrls", emptyList()),
                resources = strMap(obj, "resources"),
                noframes = bool(obj, "noframes", false),
                legacyCompat = bool(obj, "legacyCompat", false)
            ).takeIf { it.name.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}











data class Plugin(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerializedName("kind")
    val kind: PluginKind = PluginKind.HCJ,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("icon")
    val icon: String = "extension",
    @SerializedName("versionName")
    val versionName: String = "1.0.0",
    @SerializedName("authorName")
    val authorName: String = "",
    @SerializedName("homepage")
    val homepage: String = "",

    @SerializedName("packageDir")
    val packageDir: String = "",
    @SerializedName("matches")
    val matches: List<PluginMatchRule> = emptyList(),
    @SerializedName("runAt")
    val runAt: PluginRunAt = PluginRunAt.DOCUMENT_END,
    @SerializedName("permissions")
    val permissions: List<PluginPermission> = emptyList(),

    @SerializedName("builtIn")
    val builtIn: Boolean = false,
    @SerializedName("showInToolbar")
    val showInToolbar: Boolean = true,

    
    @SerializedName("entryStyle")
    val entryStyle: PluginEntryStyle = PluginEntryStyle.TOOLBAR,
    @SerializedName("panelStyle")
    val panelStyle: PluginPanelStyle = PluginPanelStyle.BOTTOM_SHEET,

    @SerializedName("hasPanel")
    val hasPanel: Boolean = false,
    @SerializedName("hasCss")
    val hasCss: Boolean = false,

    
    @SerializedName("gmGrants")
    val gmGrants: List<String> = emptyList(),
    @SerializedName("requireUrls")
    val requireUrls: List<String> = emptyList(),
    @SerializedName("resources")
    val resources: Map<String, String> = emptyMap(),
    @SerializedName("noframes")
    val noframes: Boolean = false,

    
    @SerializedName("legacyCompat")
    val legacyCompat: Boolean = false,

    
    @SerializedName("chromeExtId")
    val chromeExtId: String = "",
    @SerializedName("manifestJson")
    val manifestJson: String = "",
    @SerializedName("backgroundScript")
    val backgroundScript: String = "",
    @SerializedName("popupPath")
    val popupPath: String = "",
    @SerializedName("optionsPagePath")
    val optionsPagePath: String = "",
    @SerializedName("world")
    val world: String = "ISOLATED",

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun matchesUrl(url: String): Boolean {
        if (matches.isEmpty()) return true
        var hasInclude = false
        for (rule in matches) {
            if (rule.exclude) {
                if (pluginGlobMatches(url, rule)) return false
            } else {
                hasInclude = true
            }
        }
        if (!hasInclude) return true
        for (rule in matches) {
            if (!rule.exclude && pluginGlobMatches(url, rule)) return true
        }
        return false
    }

    fun hasPermission(permission: PluginPermission): Boolean =
        permission in permissions

    
    val isScriptPlugin: Boolean
        get() = kind == PluginKind.HCJ || kind == PluginKind.USERSCRIPT

    companion object {
        private val gson get() = GsonProvider.gson

        fun fromJson(json: String): Plugin? = try {
            gson.fromJson(json, Plugin::class.java)
        } catch (e: Exception) {
            null
        }

        




        fun fromManifest(
            manifest: PluginManifest,
            packageDir: String,
            kind: PluginKind = PluginKind.HCJ,
            hasPanel: Boolean,
            hasCss: Boolean,
            builtIn: Boolean = false
        ): Plugin = Plugin(
            id = manifest.resolvedId(packageDir),
            kind = kind,
            name = manifest.name,
            description = manifest.description,
            icon = manifest.icon,
            versionName = manifest.version,
            authorName = manifest.author,
            homepage = manifest.homepage,
            packageDir = packageDir,
            matches = manifest.matchRules(),
            runAt = manifest.resolvedRunAt(),
            permissions = manifest.resolvedPermissions().toList(),
            builtIn = builtIn,
            showInToolbar = manifest.toolbar,
            entryStyle = PluginEntryStyle.parse(manifest.preferredEntry),
            panelStyle = PluginPanelStyle.parse(manifest.preferredPanel),
            hasPanel = hasPanel,
            hasCss = hasCss,
            gmGrants = manifest.gmGrants,
            requireUrls = manifest.requireUrls,
            resources = manifest.resources,
            noframes = manifest.noframes,
            legacyCompat = manifest.legacyCompat
        )
    }
}






data class PluginPayload(
    val plugin: Plugin,
    val mainJs: String = "",
    val css: String = "",
    val panelHtml: String = ""
)

internal fun String.escapeForJsSingleQuote(): String =
    replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

internal fun String.escapeForJsTemplate(): String =
    replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("\${", "\\\${")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
