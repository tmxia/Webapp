package com.webtoapp.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.webtoapp.data.converter.Converters
import com.webtoapp.data.model.WebViewConfig
import java.io.File







object ConfigTemplateStore {

    data class ConfigTemplate(
        val name: String,
        val createdAt: Long,
        val webViewConfig: WebViewConfig
    )

    private const val FILE_NAME = "config_templates.json"
    private const val MAX_NAME_LENGTH = 40

    
    const val FILE_KIND = "webtoapp-config-template"
    private const val FILE_VERSION = 1

    private val templatesType = object : TypeToken<List<ConfigTemplate>>() {}.type
    private val prettyJson = GsonBuilder().setPrettyPrinting().create()

    private fun storeFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun list(context: Context): List<ConfigTemplate> {
        val file = storeFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            Converters.gson.fromJson<List<ConfigTemplate>>(file.readText(), templatesType) ?: emptyList()
        }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }

    fun get(context: Context, name: String): ConfigTemplate? =
        list(context).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    
    fun save(context: Context, name: String, config: WebViewConfig): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return false
        val templates = list(context).filterNot { it.name.equals(trimmed, ignoreCase = true) } +
            ConfigTemplate(trimmed, System.currentTimeMillis(), config)
        return write(context, templates)
    }

    fun delete(context: Context, name: String): Boolean {
        val templates = list(context)
        val remaining = templates.filterNot { it.name.equals(name.trim(), ignoreCase = true) }
        if (remaining.size == templates.size) return false
        return write(context, remaining)
    }

    fun rename(context: Context, oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return false
        val templates = list(context)
        val target = templates.firstOrNull { it.name.equals(oldName.trim(), ignoreCase = true) } ?: return false
        if (templates.any { it.name.equals(trimmed, ignoreCase = true) && it !== target }) return false
        val updated = templates.map { if (it === target) it.copy(name = trimmed) else it }
        return write(context, updated)
    }

    private fun write(context: Context, templates: List<ConfigTemplate>): Boolean = runCatching {
        storeFile(context).writeText(Converters.gson.toJson(templates))
        true
    }.getOrDefault(false)

    
    fun encode(name: String, createdAt: Long, config: WebViewConfig): String {
        val root = JsonObject()
        root.addProperty("kind", FILE_KIND)
        root.addProperty("version", FILE_VERSION)
        root.addProperty("name", name.trim().ifEmpty { "Current" }.take(MAX_NAME_LENGTH))
        root.addProperty("createdAt", createdAt)
        root.add("webViewConfig", Converters.gson.toJsonTree(config))
        return prettyJson.toJson(root)
    }

    fun encode(template: ConfigTemplate): String =
        encode(template.name, template.createdAt, template.webViewConfig)

    




    fun decode(text: String): ConfigTemplate? = runCatching {
        val root = JsonParser.parseString(text).asJsonObject
        val kind = root.get("kind")?.takeIf { !it.isJsonNull }?.asString
        val wrapped = root.get("webViewConfig")?.takeIf { it.isJsonObject }?.asJsonObject
        val configElement = when {
            kind == FILE_KIND && wrapped != null -> wrapped
            kind == null && wrapped != null && looksLikeConfig(wrapped) -> wrapped
            kind == null && looksLikeConfig(root) -> root
            else -> return null
        }
        val config = Converters.gson.fromJson(configElement, WebViewConfig::class.java) ?: return null
        val name = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            .ifEmpty { "Imported" }
            .take(MAX_NAME_LENGTH)
        val createdAt = root.get("createdAt")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong
            ?: System.currentTimeMillis()
        ConfigTemplate(name, createdAt, config)
    }.getOrNull()

    



    fun importJson(context: Context, text: String): ConfigTemplate? {
        val decoded = decode(text) ?: return null
        val name = uniqueName(context, decoded.name)
        if (!save(context, name, decoded.webViewConfig)) return null
        return get(context, name)
    }

    private fun uniqueName(context: Context, raw: String): String {
        val base = raw.trim().take(MAX_NAME_LENGTH).ifEmpty { "Imported" }
        if (get(context, base) == null) return base
        var index = 2
        while (index < 100) {
            val suffix = " $index"
            val candidate = base.take(MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix
            if (get(context, candidate) == null) return candidate
            index++
        }
        return base
    }

    private fun looksLikeConfig(obj: JsonObject): Boolean =
        obj.has("javaScriptEnabled") || obj.has("userAgent") || obj.has("desktopMode")
}
