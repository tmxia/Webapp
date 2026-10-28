package com.webtoapp.core.agent.tool

import android.content.Context
import com.webtoapp.core.agent.files.ProjectFileManager
import com.webtoapp.core.agent.permission.PermissionPrompter
import com.webtoapp.core.agent.todo.TodoManager
import com.webtoapp.data.model.ApiKeyConfig
import com.webtoapp.data.model.SavedModel
import com.webtoapp.data.repository.WebAppRepository

data class ToolContext(
    val androidContext: Context,
    val sessionId: String,
    val fileManager: ProjectFileManager,

    val textModel: SavedModel,
    val textApiKey: ApiKeyConfig,

    



    val textKeyPool: List<ApiKeyConfig> = emptyList(),

    val imageModel: SavedModel? = null,
    val imageApiKey: ApiKeyConfig? = null,
    val imageKeyPool: List<ApiKeyConfig> = emptyList(),

    val prompter: PermissionPrompter,

    val todos: TodoManager,

    val appRepository: WebAppRepository,

    
    
    
    
    val readFiles: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf()),

    val activePlanFile: String? = null,

    
    
    
    
    
    
    
    
    val livePlanFileRef: java.util.concurrent.atomic.AtomicReference<String?> =
        java.util.concurrent.atomic.AtomicReference(null),

    val progress: suspend (String) -> Unit = NO_OP_PROGRESS
) {

    
    fun setActivePlanFile(path: String?) {
        livePlanFileRef.set(path)
    }

    
    fun effectivePlanFile(): String? = livePlanFileRef.get() ?: activePlanFile

    fun resolveSafePath(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val cleaned = rawPath.trim().trimStart('/').trim('\\')
        if (cleaned.isEmpty()) return null
        if (cleaned.length > MAX_PATH_LENGTH) return null
        if (cleaned.contains("..")) return null
        if (cleaned.contains(':')) return null

        if (cleaned.startsWith('~')) return null
        return cleaned.replace('\\', '/')
    }

    companion object {
        const val MAX_PATH_LENGTH = 500

        val NO_OP_PROGRESS: suspend (String) -> Unit = {  }
    }
}
