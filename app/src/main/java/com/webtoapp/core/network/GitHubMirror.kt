package com.webtoapp.core.network







object GitHubMirror {

    





    data class MirrorChannel(val id: String, val prefix: String) {
        fun rewrite(url: String): String = prefix + url

        companion object {
            val DIRECT = MirrorChannel("direct", "")
        }
    }

    















    val CN_PROXIES: List<MirrorChannel> = listOf(
        MirrorChannel("gh-proxy", "https://gh-proxy.com/"),
        MirrorChannel("gh.zwy.one", "https://gh.zwy.one/"),
        MirrorChannel("gh.llkk.cc", "https://gh.llkk.cc/")
    )

    
    val ALL_CHANNELS: List<MirrorChannel> = CN_PROXIES + MirrorChannel.DIRECT

    const val NPM_REGISTRY = "https://registry.npmjs.org"
    const val NPM_MIRROR_REGISTRY = "https://registry.npmmirror.com"

    








    fun proxiedCn(url: String): List<String> {
        if (!url.startsWith("https://github.com/")) return listOf(url)
        val ordered = CnMirrorProbe.orderedChannels()
        return (ordered.map { it.rewrite(url) } + url).distinct()
    }

    















    fun proxiedCnGitHubHost(url: String): List<String> {
        if (!isGitHubHost(url)) return listOf(url)
        val ordered = CnMirrorProbe.peekChannels()
        return (ordered.map { it.rewrite(url) } + url).distinct()
    }

    private fun isGitHubHost(url: String): Boolean {
        val host = url
            .substringAfter("https://", "")
            .substringBefore("/")
            .lowercase()
        if (host.isEmpty()) return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host.endsWith(".github.com") ||
            host.endsWith("githubusercontent.com")
    }

    



    fun npmTarballUrls(url: String, cn: Boolean): List<String> {
        if (!url.startsWith("$NPM_REGISTRY/")) return listOf(url)
        if (!cn) return listOf(url)
        val mirrored = NPM_MIRROR_REGISTRY + url.removePrefix(NPM_REGISTRY)
        return listOf(mirrored, url)
    }
}
