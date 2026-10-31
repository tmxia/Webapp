package com.webtoapp.core.kernel

import com.webtoapp.core.logging.AppLogger
import com.webtoapp.data.model.UserAgentMode












data class BrowserIdentity(
    val userAgent: String?,
    val profile: KernelFlavorProfile?
) {
    val isNoOp: Boolean
        get() = userAgent.isNullOrBlank() && profile == null

    companion object {
        
        val SYSTEM_DEFAULT = BrowserIdentity(userAgent = null, profile = null)
    }
}

















object BrowserIdentityResolver {

    private const val TAG = "BrowserIdentityResolver"

    fun resolve(
        flavor: KernelFlavor,
        legacyMode: UserAgentMode,
        customUserAgent: String?,
        desktopMode: Boolean,
        desktopUserAgent: String?,
        legacyUserAgent: String?,
        deviceDisguiseUserAgent: String?
    ): BrowserIdentity {
        
        
        
        if (legacyMode == UserAgentMode.CUSTOM) {
            describe(customUserAgent)?.let { return it }
        }

        
        describe(deviceDisguiseUserAgent)?.let { return it }

        
        
        val effectiveFlavor = if (flavor != KernelFlavor.SYSTEM_DEFAULT) {
            flavor
        } else {
            legacyMode.toKernelFlavor()
        }
        if (effectiveFlavor != KernelFlavor.SYSTEM_DEFAULT) {
            val profile = effectiveFlavor.profile
            profile.userAgent?.takeIf { it.isNotBlank() }?.let {
                return BrowserIdentity(it, profile)
            }
        }

        
        if (desktopMode) {
            describe(desktopUserAgent)?.let { return it }
        }

        
        describe(legacyUserAgent)?.let { return it }

        
        return BrowserIdentity.SYSTEM_DEFAULT
    }

    







    private fun describe(userAgent: String?): BrowserIdentity? {
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: return null
        val profile = UserAgentProfileDeriver.derive(ua)
        if (profile == null) {
            AppLogger.w(
                TAG,
                "Unrecognised platform in a custom User-Agent; keeping the engine default rather " +
                    "than sending a UA whose client hints would contradict it: ${ua.take(80)}"
            )
            return null
        }
        return BrowserIdentity(ua, profile)
    }
}











internal object UserAgentProfileDeriver {

    private val CHROME_VERSION = Regex("""Chrome/(\d+)""")
    private val EDGE_VERSION = Regex("""Edg(?:A|ios)?/(\d+)""")
    private val FIREFOX_VERSION = Regex("""Firefox/(\d+)""")
    private val SAFARI_VERSION = Regex("""Version/(\d+)""")

    private val WINDOWS = Regex("""Windows NT (\d+)\.(\d+)""")
    private val MACOS = Regex("""Mac OS X (\d+)[_.](\d+)""")
    private val IOS = Regex("""(?:iPhone|iPad); CPU (?:iPhone )?OS (\d+)[_.](\d+)""")
    private val ANDROID = Regex("""Android (\d+)""")

    fun derive(userAgent: String): KernelFlavorProfile? {
        val platform = platformOf(userAgent) ?: return null
        val chromeMajor = CHROME_VERSION.find(userAgent)?.groupValues?.get(1)
        val isChromium = chromeMajor != null
        val isEdge = EDGE_VERSION.containsMatchIn(userAgent)

        val brands = when {
            isEdge && chromeMajor != null -> listOf(
                KernelBrand("Not_A Brand", "24", "24.0.0.0"),
                KernelBrand("Chromium", chromeMajor, "$chromeMajor.0.0.0"),
                KernelBrand("Microsoft Edge", chromeMajor, "$chromeMajor.0.0.0")
            )
            isChromium && chromeMajor != null -> listOf(
                KernelBrand("Not_A Brand", "24", "24.0.0.0"),
                KernelBrand("Chromium", chromeMajor, "$chromeMajor.0.0.0"),
                KernelBrand("Google Chrome", chromeMajor, "$chromeMajor.0.0.0")
            )
            
            
            else -> emptyList()
        }

        return KernelFlavorProfile(
            flavor = KernelFlavor.SYSTEM_DEFAULT,
            userAgent = userAgent,
            vendor = when {
                isChromium -> "Google Inc."
                userAgent.contains("Safari") -> "Apple Computer, Inc."
                else -> ""
            },
            hasWindowChrome = isChromium,
            supportsClientHints = isChromium,
            brands = brands,
            mobile = userAgent.contains("Mobile"),
            platform = platform.first,
            platformVersion = platform.second,
            fullVersion = if (isChromium && chromeMajor != null) "$chromeMajor.0.0.0" else "",
            architecture = architectureOf(userAgent, platform.first),
            bitness = "64",
            model = ""
        )
    }

    





    private fun architectureOf(userAgent: String, platform: String): String {
        val desktop = platform == "Windows" || platform == "macOS" || platform == "Linux"
        if (!desktop) return ""
        if (userAgent.contains("aarch64") || userAgent.contains("ARM64")) return "arm"
        if (
            platform == "Windows" ||
            userAgent.contains("Win64") ||
            userAgent.contains("x64") ||
            userAgent.contains("x86_64") ||
            userAgent.contains("Intel")
        ) {
            return "x86"
        }
        return ""
    }

    
    private fun platformOf(userAgent: String): Pair<String, String>? {
        ANDROID.find(userAgent)?.let {
            return "Android" to "${it.groupValues[1]}.0.0"
        }
        IOS.find(userAgent)?.let {
            return "iOS" to "${it.groupValues[1]}.${it.groupValues[2]}.0"
        }
        WINDOWS.find(userAgent)?.let {
            return "Windows" to "${it.groupValues[1]}.${it.groupValues[2]}.0"
        }
        MACOS.find(userAgent)?.let {
            return "macOS" to "${it.groupValues[1]}.${it.groupValues[2]}.0"
        }
        if (userAgent.contains("Linux")) return "Linux" to ""
        return null
    }
}
