package com.webtoapp.core.linux

import android.content.Context
import java.io.File
















object RuntimeExecPolicy {

    fun canExecAppDataBinaries(context: Context): Boolean {
        val target = try {
            context.applicationInfo.targetSdkVersion
        } catch (_: Exception) {
            28
        }
        return target < 29
    }

    





    fun hasMuslExecBridge(context: Context): Boolean {
        val linker = File(context.applicationInfo.nativeLibraryDir, "libmusl-linker.so")
        return linker.exists() && linker.canExecute()
    }

    







    fun hasStaticExecBridge(context: Context): Boolean {
        val primaryAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        if (!primaryAbi.startsWith("arm64-v8a")) return false
        val lib = File(context.applicationInfo.nativeLibraryDir, "libstatic_exec.so")
        return lib.exists() && lib.canRead()
    }

    



    fun restrictionNote(): String =
        " [受 targetSdk≥29 SELinux 限制，无法执行应用数据目录中的本地运行时]"

    fun hostPreviewBlockedMessage(runtimeName: String): String =
        "当前构建 targetSdk≥29，系统安全策略禁止从应用数据目录启动 $runtimeName，本地服务器预览不可用。" +
            "该工具链仅用于宿主侧处理，导出的 APK 不依赖它"
}
