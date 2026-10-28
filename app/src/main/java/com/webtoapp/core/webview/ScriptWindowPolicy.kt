package com.webtoapp.core.webview

import com.webtoapp.data.model.NewWindowBehavior








internal object ScriptWindowPolicy {

    fun keepRealPopup(hitTestUrl: String?, behavior: NewWindowBehavior): Boolean {
        if (behavior == NewWindowBehavior.BLOCK) return false
        if (behavior == NewWindowBehavior.POPUP_WINDOW) return true
        
        if (hitTestUrl.isNullOrBlank()) return true
        return OAuthProviderHosts.isOAuthHost(hitTestUrl)
    }
}
