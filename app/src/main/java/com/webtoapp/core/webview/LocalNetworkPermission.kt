package com.webtoapp.core.webview

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat












object LocalNetworkPermission {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun isPrivateNetworkUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = try {
            android.net.Uri.parse(url).host ?: return false
        } catch (_: Exception) {
            return false
        }
        return isPrivateNetworkHost(host)
    }

    fun isPrivateNetworkHost(host: String): Boolean {
        val h = host.trim().lowercase().removeSuffix(".")
        if (h.isEmpty()) return false

        
        if (h == "localhost" || h == "::1" || h == "127.0.0.1" || h.startsWith("127.")) return false

        if (h == "local" || h.endsWith(".local")) return true

        val v4 = h.removeSurrounding("[", "]")
        val parts = v4.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return when {
                a == 10 -> true                              
                a == 192 && b == 168 -> true                 
                a == 172 && b in 16..31 -> true              
                a == 169 && b == 254 -> true                 
                else -> false
            }
        }

        
        if (!h.contains(".")) {
            val firstGroup = h.substringBefore(":").takeIf { it.isNotEmpty() }?.toIntOrNull(16)
            if (firstGroup != null) {
                if ((firstGroup shr 8) == 0xfc || (firstGroup shr 8) == 0xfd) return true
                if ((firstGroup and 0xffc0) == 0xfe80) return true
            }
        }
        return false
    }

    fun isLocalNetworkBlockedError(description: String?): Boolean {
        return description?.contains("ERR_LOCAL_NETWORK", ignoreCase = true) == true
    }

    




    fun shouldRequest(context: Context, failedUrl: String?, errorDescription: String? = null): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        if (isGranted(context)) return false
        return isLocalNetworkBlockedError(errorDescription) || isPrivateNetworkUrl(failedUrl)
    }

    fun request(activity: Activity): Boolean {
        return try {
            activity.requestPermissions(arrayOf(PERMISSION), 4354)
            true
        } catch (_: Exception) {
            false
        }
    }
}
