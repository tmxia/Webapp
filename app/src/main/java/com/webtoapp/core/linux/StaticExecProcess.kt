package com.webtoapp.core.linux

import android.os.ParcelFileDescriptor
import com.webtoapp.core.logging.AppLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger







internal object StaticExecBridge {
    private var loaded = false

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("static_exec")
            loaded = true
            true
        } catch (t: Throwable) {
            AppLogger.e("StaticExecBridge", "libstatic_exec.so unavailable: ${t.message}", t)
            false
        }
    }

    
    external fun nativeSpawn(
        path: String, argv: Array<String>, envp: Array<String>, cwd: String?,
        fdIn: Int, fdOut: Int, fdErr: Int, err: IntArray
    ): Long

    
    external fun nativeWaitpid(pid: Long, statusOut: IntArray?, blocking: Boolean): Int

    external fun nativeKill(pid: Long, sig: Int): Int
}







class StaticExecProcess private constructor(
    private val pidLong: Long,
    stdoutFd: Int,
    stderrFd: Int
) : Process() {

    @Suppress("unused") 
    private val pid: Int = pidLong.toInt()

    private val stdout = ParcelFileDescriptor.adoptFd(stdoutFd)
    private val stderr = ParcelFileDescriptor.adoptFd(stderrFd)
    private val latch = CountDownLatch(1)
    private val exitCodeHolder = AtomicInteger(Int.MIN_VALUE)

    override fun getOutputStream(): OutputStream = FileOutputStream(File("/dev/null"))

    override fun getInputStream(): InputStream = FileInputStream(stdout.fileDescriptor)

    override fun getErrorStream(): InputStream = FileInputStream(stderr.fileDescriptor)

    override fun waitFor(): Int {
        latch.await()
        return exitCodeHolder.get()
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = latch.await(timeout, unit)

    override fun exitValue(): Int {
        pollOnce()
        val exited = exitCodeHolder.get()
        if (exited == Int.MIN_VALUE) throw IllegalThreadStateException("process $pidLong has not exited")
        return exited
    }

    override fun destroy() {
        StaticExecBridge.nativeKill(pidLong, 15) 
    }

    override fun destroyForcibly(): Process {
        StaticExecBridge.nativeKill(pidLong, 9) 
        return this
    }

    override fun isAlive(): Boolean {
        pollOnce()
        return exitCodeHolder.get() == Int.MIN_VALUE
    }

    fun pidValue(): Long = pidLong

    
    private fun startReaper() {
        Thread {
            try {
                val status = IntArray(1)
                val r = StaticExecBridge.nativeWaitpid(pidLong, status, true)
                if (r == 1) finish(status[0]) else finish(128 + 15)
            } catch (_: Throwable) {
                finish(128 + 9)
            }
        }.apply { isDaemon = true; name = "wta-staticexec-reaper-$pidLong"; start() }
    }

    private fun pollOnce() {
        if (exitCodeHolder.get() != Int.MIN_VALUE) return
        val status = IntArray(1)
        if (StaticExecBridge.nativeWaitpid(pidLong, status, false) == 1) finish(status[0])
    }

    private fun finish(rawStatus: Int) {
        val code = if ((rawStatus and 0x7f) == 0) (rawStatus shr 8) and 0xff else 128 + (rawStatus and 0x7f)
        exitCodeHolder.compareAndSet(Int.MIN_VALUE, code)
        latch.countDown()
    }

    companion object {
        /**
         * Start [command] via the user-mode exec loader. [env] is the full
         * environment (caller builds it like ProcessBuilder.environment()).
         * Returns null when the loader is unavailable or the spawn fails; in
         * the latter case [errorMessage] receives a diagnostic. stdin is
         * wired to /dev/null inside the child.
         *
         * @JvmStatic so the shell-synced launcher can call this by reflection
         * without a compile-time dependency. This class stays out of generated APKs.
         */
        @JvmStatic
        fun start(
            command: List<String>,
            env: Map<String, String>,
            cwd: File?,
            errorMessage: ((String) -> Unit)? = null
        ): StaticExecProcess? {
            if (command.isEmpty()) return null
            if (!StaticExecBridge.ensureLoaded()) {
                errorMessage?.invoke("libstatic_exec.so 不可用，无法在 targetSdk≥29 构建中启动静态 PHP 运行时")
                return null
            }

            val outPipe = ParcelFileDescriptor.createPipe() 
            val errPipe = ParcelFileDescriptor.createPipe()
            val errInfo = IntArray(2)
            val envp = env.entries.map { "${it.key}=${it.value}" }.toTypedArray()
            val pid = try {
                StaticExecBridge.nativeSpawn(
                    command[0], command.toTypedArray(), envp,
                    cwd?.absolutePath, -1,
                    outPipe[1].fd, errPipe[1].fd, errInfo
                )
            } catch (t: Throwable) {
                AppLogger.e("StaticExecProcess", "nativeSpawn threw: ${t.message}", t)
                outPipe[0].close(); outPipe[1].close()
                errPipe[0].close(); errPipe[1].close()
                errorMessage?.invoke("启动失败: ${t.message}")
                return null
            }

            if (pid <= 0) {
                outPipe[0].close(); outPipe[1].close()
                errPipe[0].close(); errPipe[1].close()
                errorMessage?.invoke(spawnErrorMessage(errInfo))
                return null
            }

            
            
            
            outPipe[1].close()
            errPipe[1].close()

            
            
            
            val outRead = outPipe[0].detachFd()
            val errRead = errPipe[0].detachFd()

            
            
            val proc = StaticExecProcess(pid, outRead, errRead)
            proc.startReaper()
            return proc
        }

        private fun spawnErrorMessage(err: IntArray): String = when (val code = err.getOrElse(0) { 0 }) {
            1 -> "无法打开 PHP 二进制 (errno ${err.getOrElse(1) { 0 }})"
            2 -> "读取 PHP 二进制失败 (errno ${err.getOrElse(1) { 0 }})"
            3 -> "目标不是受支持的静态 AArch64 ELF（需 static ET_EXEC 或无依赖 static-PIE）"
            5 -> "memfd 桥创建失败 (errno ${err.getOrElse(1) { 0 }})"
            6 -> "启动参数构造失败 (errno ${err.getOrElse(1) { 0 }})"
            7 -> "同步管道创建失败 (errno ${err.getOrElse(1) { 0 }})"
            8 -> "fork 失败 (errno ${err.getOrElse(1) { 0 }})"
            in 101..199 -> "子进程加载失败 (stage ${code - 100})"
            else -> "未知错误 (code $code)"
        }
    }
}
