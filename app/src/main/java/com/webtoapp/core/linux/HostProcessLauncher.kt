package com.webtoapp.core.linux

import java.io.File
import kotlin.jvm.functions.Function1









object HostProcessLauncher {

    data class Result(
        val process: Process?,
        val error: String?
    )

    fun start(
        context: android.content.Context,
        command: List<String>,
        env: Map<String, String>,
        cwd: File?,
        runtimeLabel: String = "PHP"
    ): Result {
        val wxRestricted = !RuntimeExecPolicy.canExecAppDataBinaries(context)
        if (!wxRestricted) {
            val pb = ProcessBuilder(command)
            if (cwd != null) pb.directory(cwd)
            val pbEnv = pb.environment()
            env.forEach { (k, v) -> pbEnv[k] = v }
            return Result(pb.start(), null)
        }
        if (!RuntimeExecPolicy.hasStaticExecBridge(context)) {
            return Result(null, RuntimeExecPolicy.hostPreviewBlockedMessage(runtimeLabel))
        }
        
        
        val fullEnv = System.getenv().toMutableMap()
        fullEnv.putAll(env)
        var spawnError: String? = null
        val proc = startStaticExec(command, fullEnv, cwd) { msg -> spawnError = msg }
        return Result(proc, spawnError)
    }

    



    private fun startStaticExec(
        command: List<String>,
        env: Map<String, String>,
        cwd: File?,
        onError: (String) -> Unit
    ): Process? {
        return try {
            val clazz = Class.forName("com.webtoapp.core.linux.StaticExecProcess")
            val method = clazz.getDeclaredMethod(
                "start",
                List::class.java,
                Map::class.java,
                File::class.java,
                Function1::class.java
            )
            method.invoke(null, command, env, cwd, onError) as? Process
        } catch (t: Throwable) {
            onError("libstatic_exec.so 不可用: ${t.message}")
            null
        }
    }
}
