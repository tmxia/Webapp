package com.webtoapp.core.share












object ShareReceiveContract {

    
    const val MIME_IMAGES = "image/*"

    
    const val MIME_TEXT = "text/plain"

    
    const val INBOX_DIR_NAME = "shared_inbox"

    
    const val INDEX_FILE_NAME = "index.json"

    



    const val MAX_FILE_BYTES: Long = 64L * 1024 * 1024

    
    const val MAX_TOTAL_BYTES: Long = 192L * 1024 * 1024

    



    const val MAX_ITEMS: Int = 10

    







    const val INLINE_MAX_BYTES: Long = 5L * 1024 * 1024

    



    const val READ_CHUNK_MAX_BYTES: Int = 256 * 1024

    
    const val MAX_TEXT_CHARS: Int = 100_000

    




    const val TTL_MS: Long = 60L * 60 * 1000

    
    const val KIND_FILE = "file"

    
    const val KIND_TEXT = "text"

    







    val OPEN_WITH_MIME_TYPES = listOf(
        "text/*",
        "application/json",
        "application/xml",
        "application/x-yaml",
        "application/toml",
        "application/javascript",
        "application/x-javascript"
    )

    





    val OPEN_WITH_EXTENSIONS = listOf(
        "txt", "md", "markdown", "log", "csv", "tsv",
        "json", "json5", "xml", "yml", "yaml", "toml", "ini", "cfg", "conf",
        "properties", "prop",
        "js", "mjs", "cjs", "ts", "css",
        "py", "java", "kt", "kts",
        "c", "h", "cpp", "cc", "cxx", "hpp",
        "sh", "bash", "gradle", "go", "rs"
    )

    
    const val JS_NAMESPACE = "WTAShareInbox"

    
    const val JS_EVENT_NAME = "wta:share"

    




    const val JS_BRIDGE_NAME = "__WTAShareInboxBridge"

    
    const val FALLBACK_NAME = "shared_content"
}








data class SharedItem(
    val id: String,
    val kind: String,
    val mimeType: String,
    val name: String,
    val size: Long,
    val path: String? = null,
    val text: String? = null,
    val receivedAt: Long = System.currentTimeMillis(),
    




    val consumedAt: Long = 0
) {
    val isText: Boolean get() = kind == ShareReceiveContract.KIND_TEXT
    val isConsumed: Boolean get() = consumedAt > 0
}
