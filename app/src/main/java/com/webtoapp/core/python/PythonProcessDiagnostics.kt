package com.webtoapp.core.python













object PythonProcessDiagnostics {

    const val SIGHUP = 1
    const val SIGINT = 2
    const val SIGQUIT = 3
    const val SIGILL = 4
    const val SIGTRAP = 5
    const val SIGABRT = 6
    const val SIGBUS = 7
    const val SIGFPE = 8
    const val SIGKILL = 9
    const val SIGSEGV = 11
    const val SIGPIPE = 13
    const val SIGTERM = 15
    const val SIGSYS = 31

    
    fun signalForExitCode(exitCode: Int): Int? =
        if (exitCode >= 128) exitCode - 128 else null

    fun signalName(signal: Int): String = when (signal) {
        SIGHUP -> "SIGHUP"
        SIGINT -> "SIGINT"
        SIGQUIT -> "SIGQUIT"
        SIGILL -> "SIGILL"
        SIGTRAP -> "SIGTRAP"
        SIGABRT -> "SIGABRT"
        SIGBUS -> "SIGBUS"
        SIGFPE -> "SIGFPE"
        SIGKILL -> "SIGKILL"
        SIGSEGV -> "SIGSEGV"
        SIGPIPE -> "SIGPIPE"
        SIGTERM -> "SIGTERM"
        SIGSYS -> "SIGSYS"
        else -> "SIGNAL_$signal"
    }

    




    fun isDeterministicEnvironmentFailure(exitCode: Int): Boolean =
        signalForExitCode(exitCode) == SIGSYS
}
