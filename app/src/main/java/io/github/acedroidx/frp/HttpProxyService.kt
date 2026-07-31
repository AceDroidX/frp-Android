package io.github.acedroidx.frp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class HttpProxyService : Service() {
    companion object {
        const val PORT = 8080
        private const val TAG = "HttpProxyService"
        private const val CHANNEL_ID = "http_proxy_bg"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_START = "io.github.acedroidx.frp.proxy.START"
        private const val ACTION_STOP = "io.github.acedroidx.frp.proxy.STOP"

        fun start(context: Context) {
            val intent = Intent(context, HttpProxyService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            // 先保存关闭状态，避免服务进程在停止途中被系统回收后又被粘性重启。
            context.getSharedPreferences("data", MODE_PRIVATE).edit {
                putBoolean(PreferencesKey.HTTP_PROXY_ENABLED, false)
            }
            context.startService(
                Intent(context, HttpProxyService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferences = getSharedPreferences("data", MODE_PRIVATE)

        if (intent?.action == ACTION_STOP) {
            // 通知栏的停止按钮会直接发送 ACTION_STOP，也必须同步持久化开关状态。
            preferences.edit { putBoolean(PreferencesKey.HTTP_PROXY_ENABLED, false) }
            NativeHttpProxy.stop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // startForegroundService 后必须立即进入前台；随后再启动 native 监听器。
        startForeground(NOTIFICATION_ID, createNotification())
        val shouldRun = intent?.action == ACTION_START ||
            preferences.getBoolean(PreferencesKey.HTTP_PROXY_ENABLED, false)
        if (!shouldRun) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val error = NativeHttpProxy.start(PORT)
        if (error != null) {
            Log.e(TAG, "Unable to start native proxy: $error")
            preferences.edit { putBoolean(PreferencesKey.HTTP_PROXY_ENABLED, false) }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification(error))
            stopSelf()
            return START_NOT_STICKY
        }

        preferences.edit { putBoolean(PreferencesKey.HTTP_PROXY_ENABLED, true) }
        return START_STICKY
    }

    override fun onDestroy() {
        NativeHttpProxy.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotification(error: String? = null): Notification {
        val openIntent = Intent(this, SettingsActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, HttpProxyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.http_proxy_notification_title))
            .setContentText(
                error ?: getString(R.string.http_proxy_notification_content, PORT)
            )
            .setContentIntent(openPendingIntent)
            .setOngoing(error == null)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_baseline_delete_24,
                getString(R.string.stop),
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.http_proxy_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.http_proxy_notification_channel_desc)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
