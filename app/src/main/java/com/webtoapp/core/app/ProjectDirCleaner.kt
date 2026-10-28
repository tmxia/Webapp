package com.webtoapp.core.app

import android.content.Context
import com.webtoapp.core.golang.GoRuntime
import com.webtoapp.core.nodejs.NodeRuntime
import com.webtoapp.core.php.PhpAppRuntime
import com.webtoapp.core.python.PythonRuntime
import com.webtoapp.core.wordpress.WordPressManager
import com.webtoapp.data.model.AppType
import com.webtoapp.data.model.WebApp
import com.webtoapp.core.logging.AppLogger
import java.io.File


















object ProjectDirCleaner {

    private const val TAG = "ProjectDirCleaner"

    
    fun deleteForApp(context: Context, app: WebApp): List<File> {
        val appContext = context.applicationContext
        val sandboxRoot = appContext.filesDir.canonicalFile
        val deleted = mutableListOf<File>()

        fun deleteIfSandboxed(dir: File) {
            try {
                if (!dir.exists()) return
                val canonical = dir.canonicalFile
                
                
                if (!canonical.path.startsWith(sandboxRoot.path)) {
                    AppLogger.w(TAG, "Refusing to delete dir outside filesDir: $canonical")
                    return
                }
                if (dir.deleteRecursively()) {
                    deleted += canonical
                    AppLogger.i(TAG, "Deleted project dir: $canonical")
                } else {
                    AppLogger.w(TAG, "Failed to delete project dir: $canonical")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error deleting project dir $dir", e)
            }
        }

        when (app.appType) {
            AppType.WORDPRESS -> {
                app.wordpressConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(WordPressManager.getProjectDir(appContext, pid))
                }
            }
            AppType.NODEJS_APP -> {
                app.nodejsConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(NodeRuntime(appContext).getProjectDir(pid))
                }
            }
            AppType.PHP_APP -> {
                app.phpAppConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(PhpAppRuntime(appContext).getProjectDir(pid))
                }
            }
            AppType.PYTHON_APP -> {
                app.pythonAppConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(PythonRuntime(appContext).getProjectDir(pid))
                }
            }
            AppType.GO_APP -> {
                app.goAppConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(GoRuntime(appContext).getProjectDir(pid))
                }
            }
            AppType.FRONTEND -> {
                
                app.htmlConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "frontend_builds/$pid"))
                }
            }
            AppType.HTML, AppType.MULTI_WEB -> {
                
                
                val cfg = app.htmlConfig
                val pid = cfg?.projectId?.takeIf { it.isNotBlank() }
                val importedDir = cfg?.projectDir?.takeIf { it.isNotBlank() }?.let(::File)
                if (importedDir != null) {
                    deleteIfSandboxed(importedDir)
                } else if (pid != null) {
                    
                    
                    
                    
                    val borrowedFromSite = app.multiWebConfig?.sites?.any {
                        it.type == "EXISTING" && it.sourceProjectId.isNotBlank() && it.sourceProjectId == pid
                    } == true
                    if (borrowedFromSite) {
                        AppLogger.i(TAG, "Skip html_projects/$pid: MULTI_WEB app borrows the source app's project id")
                    } else {
                        deleteIfSandboxed(File(appContext.filesDir, "html_projects/$pid"))
                    }
                }
            }
            
            AppType.WEB, AppType.IMAGE, AppType.VIDEO, AppType.GALLERY -> { }
        }

        return deleted
    }
}
