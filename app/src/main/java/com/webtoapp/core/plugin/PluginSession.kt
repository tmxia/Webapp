package com.webtoapp.core.plugin

import android.webkit.WebView
import com.webtoapp.core.logging.AppLogger
import com.webtoapp.util.GsonProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow








object PluginHostState {

    
    data class Entry(
        val pluginId: String,
        val name: String,
        val icon: String,
        val kind: PluginKind,
        val matchesCurrentUrl: Boolean,
        val hasPanel: Boolean,
        val badge: String = "",
        val badgeColor: String = "",
        
        val entryStyle: PluginEntryStyle = PluginEntryStyle.TOOLBAR,
        val panelStyle: PluginPanelStyle = PluginPanelStyle.BOTTOM_SHEET,
        
        val menuCommands: List<String> = emptyList()
    )

    
    data class PanelRequest(
        val pluginId: String,
        val kind: PluginKind,
        
        val url: String,
        
        val html: String? = null,
        val baseUrl: String? = null,
        val panelStyle: PluginPanelStyle = PluginPanelStyle.BOTTOM_SHEET,
        val seq: Long = System.nanoTime()
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _panelRequest = MutableStateFlow<PanelRequest?>(null)
    val panelRequest: StateFlow<PanelRequest?> = _panelRequest.asStateFlow()

    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    
    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    
    @Volatile
    var session: PluginSession? = null
        private set

    internal fun publishSession(session: PluginSession?) {
        this.session = session
        if (session == null) {
            _entries.value = emptyList()
            _panelRequest.value = null
            _panelOpen.value = false
            _sheetOpen.value = false
        }
    }

    
    fun openPluginSheet() {
        _sheetOpen.value = true
    }

    fun dismissPluginSheet() {
        _sheetOpen.value = false
    }

    internal fun publishEntries(entries: List<Entry>) {
        _entries.value = entries
    }

    internal fun updateBadge(pluginId: String, text: String, color: String) {
        _entries.value = _entries.value.map {
            if (it.pluginId == pluginId) it.copy(badge = text, badgeColor = color) else it
        }
    }

    internal fun updateMenuCommands(pluginId: String, names: List<String>) {
        _entries.value = _entries.value.map {
            if (it.pluginId == pluginId) it.copy(menuCommands = names) else it
        }
    }

    internal fun requestPanel(request: PanelRequest) {
        _panelRequest.value = request
        _panelOpen.value = true
    }

    fun dismissPanel() {
        val wasOpen = _panelOpen.value
        _panelOpen.value = false
        if (wasOpen) session?.onPanelClosed?.invoke()
    }

    internal fun clearPanelRequest() {
        _panelRequest.value = null
        _panelOpen.value = false
    }
}










class PluginSession(
    private val configStore: PluginConfigStore,
    private val appLang: () -> String,
    private val pageEvaluator: (String) -> Unit,
    private val notifySink: (pluginId: String, title: String, body: String) -> Unit = { _, _, _ -> }
) {

    




    data class Resolved(
        val plugin: Plugin,
        val mainJs: String = "",
        val css: String = "",
        val panelHtml: String = "",
        


        val requireContents: Map<String, String> = emptyMap(),
        val attached: Boolean = true
    )

    val bridge = PluginBridge(configStore, BridgeHost())

    




    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun evalOnPage(js: String) {
        mainHandler.post { pageEvaluator(js) }
    }

    private fun evalOnPanel(js: String) {
        mainHandler.post { panelEvaluator?.invoke(js) }
    }

    
    @Volatile
    var panelEvaluator: ((String) -> Unit)? = null

    @Volatile
    var onPanelClosed: (() -> Unit)? = null

    @Volatile
    private var resolvedPlugins: List<Resolved> = emptyList()

    @Volatile
    private var currentUrl: String = ""

    fun setPlugins(plugins: List<Resolved>) {
        resolvedPlugins = plugins
        bridge.revokeAll()
        issuedTokens.clear()
        publishEntries()
    }

    fun onUrlChanged(url: String) {
        currentUrl = url
        publishEntries()
        
        
        
        
        
        if (url != lastNavEmittedUrl) {
            lastNavEmittedUrl = url
            val payload = org.json.JSONObject().put("url", url).toString()
            resolvedPlugins.forEach { r ->
                if (r.plugin.kind != PluginKind.CHROME_EXTENSION && r.plugin.matchesUrl(url)) {
                    evalOnPage(PluginInjection.emitEvent(r.plugin.id, "navigate", payload))
                }
            }
        }
    }

    private var lastNavEmittedUrl: String? = null

    fun pluginFor(id: String): Plugin? = resolvedPlugins.firstOrNull { it.plugin.id == id }?.plugin

    fun resolvedFor(id: String): Resolved? = resolvedPlugins.firstOrNull { it.plugin.id == id }

    private fun publishEntries() {
        PluginHostState.publishEntries(
            resolvedPlugins
                .filter { it.plugin.showInToolbar }
                .map { r ->
                    PluginHostState.Entry(
                        pluginId = r.plugin.id,
                        name = r.plugin.localizedName(),
                        icon = r.plugin.icon,
                        kind = r.plugin.kind,
                        matchesCurrentUrl = r.plugin.matchesUrl(currentUrl),
                        hasPanel = r.plugin.hasPanel,
                        entryStyle = r.plugin.entryStyle,
                        panelStyle = r.plugin.panelStyle
                    )
                }
        )
    }

    
    fun activeFor(url: String, appAttachedOnly: Boolean = false): List<Resolved> =
        resolvedPlugins.filter { !appAttachedOnly || it.attached }

    
    fun chromeExtIds(url: String, appAttachedOnly: Boolean = false): List<String> =
        activeFor(url, appAttachedOnly)
            .filter { it.plugin.kind == PluginKind.CHROME_EXTENSION && it.plugin.chromeExtId.isNotEmpty() }
            .map { it.plugin.chromeExtId }
            .distinct()

    




    fun injectionFor(runAt: PluginRunAt, url: String, appAttachedOnly: Boolean = false): String {
        val out = StringBuilder()
        val matching = activeFor(url, appAttachedOnly).filter { r ->
            r.plugin.kind == PluginKind.HCJ && r.plugin.runAt == runAt && r.plugin.matchesUrl(url)
        }
        if (matching.isEmpty()) return out.toString()
        if (runAt == PluginRunAt.DOCUMENT_START) {
            out.append(PluginInjection.BOOTSTRAP_JS).append('\n')
        }
        if (runAt == PluginRunAt.DOCUMENT_START) {
            matching.forEach { r ->
                if (r.css.isNotBlank()) {
                    out.append(PluginInjection.wrapCss(r.plugin.id, r.css)).append('\n')
                }
            }
        }
        matching.forEach { r ->
            if (r.mainJs.isNotBlank()) {
                out.append(
                    PluginInjection.wrapPluginCode(
                        plugin = r.plugin,
                        code = r.mainJs,
                        token = tokenFor(r.plugin.id),
                        appLang = appLang()
                    )
                ).append('\n')
            }
        }
        return out.toString()
    }

    private fun tokenFor(pluginId: String): String {
        
        
        return issuedTokens.getOrPut(pluginId) { bridge.issueToken(pluginId) }
    }

    private val issuedTokens = mutableMapOf<String, String>()

    
    fun emitAction(pluginId: String) {
        evalOnPage(PluginInjection.emitEvent(pluginId, "action", "null"))
    }

    
    var onChromeEntry: ((Plugin) -> Unit)? = null

    
    var userscriptAliasFor: ((String) -> String)? = null

    
    var popupWebViewFactory: ((String) -> WebView?)? = null

    
    var shareHandler: ((title: String, text: String, url: String) -> Unit)? = null

    
    var openExternalHandler: ((url: String) -> Unit)? = null

    
    var clearDataHandler: (() -> Unit)? = null

    
    var exitAppHandler: (() -> Unit)? = null

    





    fun activateEntry(pluginId: String) {
        val resolved = resolvedFor(pluginId) ?: return
        when {
            resolved.plugin.kind == PluginKind.CHROME_EXTENSION -> {
                val req = panelRequestOverride?.invoke(resolved)
                if (req != null) PluginHostState.requestPanel(req)
                else onChromeEntry?.invoke(resolved.plugin)
            }
            resolved.plugin.hasPanel -> openPanelRequest(resolved)
            else -> emitAction(pluginId)
        }
    }

    
    fun invokeMenuCommand(pluginId: String, name: String) {
        val alias = userscriptAliasFor?.invoke(pluginId) ?: pluginId
        evalOnPage(
            "(function(){var m=window.__WTA_GM_MENU__&&window.__WTA_GM_MENU__[" +
                org.json.JSONObject.quote(alias) + "];var f=m&&m[" +
                org.json.JSONObject.quote(name) + "];if(f)try{f()}catch(e){console.error('[GM menu]',e)}})();"
        )
    }

    
    fun updateMenuCommands(scriptId: String, names: List<String>) {
        PluginHostState.updateMenuCommands(scriptId, names)
    }

    
    fun userscriptsFor(runAt: PluginRunAt, url: String, appAttachedOnly: Boolean = false): List<Resolved> =
        activeFor(url, appAttachedOnly).filter { r ->
            r.plugin.kind == PluginKind.USERSCRIPT && r.plugin.runAt == runAt && r.plugin.matchesUrl(url)
        }

    
    fun deliverPanelMessageToPage(pluginId: String, json: String) {
        evalOnPage(PluginInjection.emitEvent(pluginId, "panel", json))
    }

    
    fun deliverPanelMessageToPanel(json: String) {
        evalOnPanel("window.__hcjPanelOnMsg && window.__hcjPanelOnMsg($json);")
    }

    fun destroy() {
        bridge.revokeAll()
        issuedTokens.clear()
        resolvedPlugins = emptyList()
        panelEvaluator = null
        if (PluginHostState.session === this) {
            PluginHostState.publishSession(null)
        }
    }

    private inner class BridgeHost : PluginBridge.Host {
        override fun evaluatePageJs(js: String) = evalOnPage(js)
        override fun pluginFor(pluginId: String): Plugin? = this@PluginSession.pluginFor(pluginId)

        override fun onBadge(pluginId: String, text: String, color: String) {
            PluginHostState.updateBadge(pluginId, text, color)
        }

        override fun onPanelOpen(pluginId: String) {
            val resolved = resolvedFor(pluginId) ?: return
            openPanelRequest(resolved)
        }

        override fun onPanelClose(pluginId: String) {
            PluginHostState.dismissPanel()
        }

        override fun onPanelMessage(pluginId: String, json: String) {
            deliverPanelMessageToPanel(json)
        }

        override fun onNotify(pluginId: String, title: String, body: String) {
            notifySink(pluginId, title, body)
        }

        override fun onShare(pluginId: String, title: String, text: String, url: String) {
            shareHandler?.invoke(title, text, url)
        }

        override fun onOpenExternal(pluginId: String, url: String) {
            openExternalHandler?.invoke(url)
        }

        override fun onClearData(pluginId: String) {
            clearDataHandler?.invoke()
        }

        override fun onExitApp(pluginId: String) {
            exitAppHandler?.invoke()
        }
    }

    



    
    fun attach() {
        PluginHostState.publishSession(this)
        publishEntries()
    }

    





    var panelRequestOverride: ((Resolved) -> PluginHostState.PanelRequest?)? = null

    



    fun createPanelBridge(pluginId: String): PluginPanelBridge =
        PluginPanelBridge(
            pluginId = pluginId,
            configStore = configStore,
            hasPermission = { p -> pluginFor(pluginId)?.hasPermission(p) == true },
            host = object : PluginPanelBridge.Host {
                override fun onPanelSend(pluginId: String, json: String) {
                    deliverPanelMessageToPage(pluginId, json)
                }

                override fun onPanelClose(pluginId: String) {
                    PluginHostState.dismissPanel()
                }
            }
        )

    private fun openPanelRequest(resolved: Resolved) {
        val req = (panelRequestOverride?.invoke(resolved)
            ?: PluginHostState.PanelRequest(
                pluginId = resolved.plugin.id,
                kind = resolved.plugin.kind,
                url = "",
                html = resolved.panelHtml.takeIf { it.isNotBlank() },
                baseUrl = "hcj-plugin://${resolved.plugin.id}/"
            )).copy(panelStyle = resolved.plugin.panelStyle)
        PluginHostState.requestPanel(req)
    }
}
