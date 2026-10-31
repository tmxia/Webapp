package com.webtoapp.core.apkbuilder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.webtoapp.ui.MainActivity
import com.webtoapp.R
import com.webtoapp.core.i18n.Strings
import com.webtoapp.core.logging.AppLogger
import com.webtoapp.util.SafeNotificationChannels













class ApkBuildService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val appName = intent?.getStringExtra(EXTRA_APP_NAME).orEmpty()
        ensureChannel()
        promoteToForeground(buildNotification(Strings.buildServiceRunning(appName)))
        acquireWakeLock()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        inForeground = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        SafeNotificationChannels.ensure(
            context = this,
            id = CHANNEL_ID,
            name = Strings.buildServiceChannelName,
            importance = android.app.NotificationManager.IMPORTANCE_LOW
        )
    }

    private fun promoteToForeground(notification: Notification) {
        if (inForeground) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            inForeground = true
        } catch (e: Exception) {
            
            
            AppLogger.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(Strings.buildServiceTitle)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebToApp:ApkBuild").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "ApkBuildService"
        private const val CHANNEL_ID = "apk_build"
        private const val NOTIFICATION_ID = 1301
        private const val ACTION_STOP = "com.webtoapp.action.APK_BUILD_STOP"
        private const val EXTRA_APP_NAME = "app_name"

        
        
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
        private const val PROGRESS_THROTTLE_MS = 250L

        @Volatile private var lastNotifyAt = 0L
        @Volatile private var lastNotifyPercent = -1

        fun start(context: Context, appName: String) {
            try {
                SafeNotificationChannels.ensure(
                    context = context,
                    id = CHANNEL_ID,
                    name = Strings.buildServiceChannelName,
                    importance = android.app.NotificationManager.IMPORTANCE_LOW
                )
                val intent = Intent(context, ApkBuildService::class.java)
                    .putExtra(EXTRA_APP_NAME, appName)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AppLogger.w(TAG, "startForegroundService rejected — build continues without keep-alive", e)
            }
        }

        




        fun updateProgress(context: Context, percent: Int, text: String) {
            val now = System.currentTimeMillis()
            if (percent == lastNotifyPercent && now - lastNotifyAt < PROGRESS_THROTTLE_MS) return
            lastNotifyAt = now
            lastNotifyPercent = percent
            try {
                val appContext = context.applicationContext
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(Strings.buildServiceTitle)
                    .setContentText("$percent% · $text")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, percent.coerceIn(0, 100), false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                AppLogger.w(TAG, "progress notify failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            lastNotifyPercent = -1
            
            
            try {
                context.stopService(Intent(context, ApkBuildService::class.java))
            } catch (e: Exception) {
                AppLogger.w(TAG, "stop request failed: ${e.message}")
            }
            runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        }
    }
}
