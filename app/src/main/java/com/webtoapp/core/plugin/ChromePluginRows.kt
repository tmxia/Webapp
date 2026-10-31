package com.webtoapp.core.plugin

import com.webtoapp.core.extension.ExtensionModule








private val CONTENT_SCRIPT_SUFFIX = Regex("""\s\[\d+]$""")


internal fun stripChromeContentScriptSuffix(name: String): String =
    name.replace(CONTENT_SCRIPT_SUFFIX, "")







internal fun chromePluginFromModules(modules: List<ExtensionModule>): Plugin? {
    val group = modules.filter { it.chromeExtId.isNotBlank() }
    if (group.isEmpty()) return null
    val first = group.first()
    fun pick(select: (ExtensionModule) -> String): String =
        group.firstNotNullOfOrNull { select(it).takeIf(String::isNotBlank) }.orEmpty()
    val created = group.map { it.createdAt }.filter { it > 0L }
    return Plugin(
        id = first.id.ifBlank { first.chromeExtId },
        kind = PluginKind.CHROME_EXTENSION,
        name = stripChromeContentScriptSuffix(pick { it.name }.ifBlank { first.name }),
        description = pick { it.description },
        icon = pick { it.icon }.ifBlank { "extension" },
        versionName = group.firstNotNullOfOrNull { it.version.name.takeIf(String::isNotBlank) } ?: "1.0.0",
        authorName = group.firstNotNullOfOrNull { it.author?.name?.takeIf(String::isNotBlank) }.orEmpty(),
        chromeExtId = first.chromeExtId,
        manifestJson = pick { it.manifestJson },
        backgroundScript = pick { it.backgroundScript },
        popupPath = pick { it.popupPath },
        optionsPagePath = pick { it.optionsPagePath },
        world = pick { it.world }.ifBlank { "ISOLATED" },
        showInToolbar = true,
        createdAt = created.minOrNull() ?: first.createdAt,
        updatedAt = group.maxOf { it.updatedAt }
    )
}

internal data class ChromePluginCollapse(
    val plugins: List<Plugin>,
    val enabledIds: List<String>,
    val aliases: Map<String, String>,
    val changed: Boolean
)






internal fun collapseChromePlugins(
    plugins: List<Plugin>,
    enabledIds: List<String>,
    existingAliases: Map<String, String> = emptyMap()
): ChromePluginCollapse {
    val keptAt = linkedMapOf<String, Int>()
    val out = ArrayList<Plugin>(plugins.size)
    val removedToKept = linkedMapOf<String, String>()
    var changed = false

    for (plugin in plugins) {
        val extId = plugin.chromeExtId
        if (plugin.kind != PluginKind.CHROME_EXTENSION || extId.isBlank()) {
            out.add(plugin)
            continue
        }
        val index = keptAt[extId]
        if (index == null) {
            val stripped = stripChromeContentScriptSuffix(plugin.name)
            val next = if (stripped == plugin.name) plugin else plugin.copy(name = stripped)
            if (next != plugin) changed = true
            keptAt[extId] = out.size
            out.add(next)
        } else {
            val merged = mergeChromeFields(out[index], plugin)
            if (merged != out[index]) out[index] = merged
            if (plugin.id != merged.id) removedToKept[plugin.id] = merged.id
            changed = true
        }
    }

    val aliases = LinkedHashMap<String, String>()
    for ((from, to) in existingAliases) {
        if (from.isBlank() || from in removedToKept) continue
        val target = removedToKept[to] ?: to
        if (from != target && target.isNotBlank()) aliases[from] = target
    }
    for ((from, to) in removedToKept) {
        if (from.isNotBlank() && from != to) aliases[from] = to
    }
    if (aliases != existingAliases) changed = true

    val remapped = remapEnabledIds(enabledIds, removedToKept)
    if (remapped != enabledIds) changed = true
    return ChromePluginCollapse(out, remapped, aliases, changed)
}

private fun mergeChromeFields(keep: Plugin, extra: Plugin): Plugin = keep.copy(
    description = keep.description.ifBlank { extra.description },
    icon = keep.icon.ifBlank { extra.icon }.ifBlank { "extension" },
    versionName = keep.versionName.ifBlank { extra.versionName },
    authorName = keep.authorName.ifBlank { extra.authorName },
    manifestJson = keep.manifestJson.ifBlank { extra.manifestJson },
    backgroundScript = keep.backgroundScript.ifBlank { extra.backgroundScript },
    popupPath = keep.popupPath.ifBlank { extra.popupPath },
    optionsPagePath = keep.optionsPagePath.ifBlank { extra.optionsPagePath },
    world = keep.world.ifBlank { extra.world }.ifBlank { "ISOLATED" },
    createdAt = listOf(keep.createdAt, extra.createdAt).filter { it > 0L }.minOrNull()
        ?: keep.createdAt,
    updatedAt = maxOf(keep.updatedAt, extra.updatedAt)
)


internal fun remapEnabledIds(
    enabledIds: List<String>,
    removedToKept: Map<String, String>
): List<String> {
    val out = ArrayList<String>(enabledIds.size)
    for (id in enabledIds) {
        val mapped = removedToKept[id] ?: id
        if (mapped.isNotBlank() && mapped !in out) out.add(mapped)
    }
    return out
}












internal fun releasePluginOrderHold(
    hold: List<String>?,
    shownIds: List<String>,
    dragging: Boolean
): List<String>? {
    if (hold == null || dragging) return hold
    return if (shownIds == hold || shownIds.toSet() != hold.toSet()) null else hold
}

internal fun syncPluginWorkingCopy(
    working: List<Plugin>,
    fromStore: List<Plugin>,
    holdWorkingOrder: Boolean
): List<Plugin> {
    if (!holdWorkingOrder) return fromStore
    val storeById = fromStore.associateBy { it.id }
    val kept = working.mapNotNull { storeById[it.id] }
    if (kept.size == working.size && kept.size == fromStore.size) return kept
    val keptIds = kept.map { it.id }.toSet()
    return kept + fromStore.filter { it.id !in keptIds }
}
