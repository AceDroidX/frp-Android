package io.github.acedroidx.frp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.*

class ShellService : Service() {
    var p: Process? = null


    // Binder given to clients
    private val binder = LocalBinder()

    // Random number generator
    private val mGenerator = Random()

    /** method for clients  */
    val randomNumber: Int
        get() = mGenerator.nextInt(100)

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder(), IBinder {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): ShellService = this@ShellService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var filename = ""
        if (p != null) {
            Log.w("adx", "process isn't null,service won't start")
            Toast.makeText(this, "process isn't null,service won't start", Toast.LENGTH_SHORT)
                .show()
            return START_NOT_STICKY
        }
        if (intent != null) {
            filename = intent.extras?.get("filename") as String
        } else {
            filename = "Error:filename unknown!!!"
            Log.e("adx", filename)
            Toast.makeText(this, filename, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        val ainfo =
            packageManager.getApplicationInfo(packageName, PackageManager.GET_SHARED_LIBRARY_FILES)
        Log.d("adx", "native library dir ${ainfo.nativeLibraryDir}")
        try {
            p = Runtime.getRuntime().exec(
                "${ainfo.nativeLibraryDir}/${filename} -c config.ini", arrayOf(""), this.filesDir
            )
        } catch (e: Exception) {
            Log.e("adx", e.stackTraceToString())
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        Toast.makeText(this, "已启动服务", Toast.LENGTH_SHORT).show()
        startForeground(1, showMotification());
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        p?.destroy()
        p = null
        Toast.makeText(this, "已关闭服务", Toast.LENGTH_SHORT).show()
    }

    private fun showMotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }
        val notification = NotificationCompat.Builder(this, "shell_bg")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("frp后台服务")
            .setContentText("已启动frp")
            //.setTicker("test")
            .setContentIntent(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        } else {
            return notification.build()
        }
    }
}