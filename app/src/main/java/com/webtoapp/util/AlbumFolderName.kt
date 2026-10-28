package com.webtoapp.util





object AlbumFolderName {
    const val DEFAULT = "WebToApp"

    fun sanitize(raw: String?): String {
        val cleaned = raw.orEmpty()
            .trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "")
            .trim('.')
            .take(64)
            .trim()
        return cleaned.ifBlank { DEFAULT }
    }
}
