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
import java.io.File
import java.util.Random


class ShellService : Service() {
    private var process_thread: Thread? = null
    private val outputBuilder = StringBuilder()
    fun getOutput(): String {
        return outputBuilder.toString()
    }

    fun clearOutput() {
        outputBuilder.clear()
    }

    fun getIsRunning(): Boolean {
        return process_thread != null
    }

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
        when (intent?.action) {
            ShellServiceAction.START -> {
                if (process_thread != null) {
                    Log.w("adx", "process isn't null,service won't start")
                    Toast.makeText(
                        this, "process isn't null,service won't start", Toast.LENGTH_SHORT
                    ).show()
                    return START_NOT_STICKY
                }
                val filename = intent.extras?.getString("filename")
                if (filename == null) {
                    Log.w("adx", "filename is null,service won't start")
                    Toast.makeText(this, "filename is null,service won't start", Toast.LENGTH_SHORT)
                        .show()
                    return START_NOT_STICKY
                }
                val ainfo = packageManager.getApplicationInfo(
                    packageName, PackageManager.GET_SHARED_LIBRARY_FILES
                )
                Log.d("adx", "native library dir ${ainfo.nativeLibraryDir}")
                try {
                    runCommand(
                        "${ainfo.nativeLibraryDir}/${filename} -c ${BuildConfig.ConfigFileName}",
                        arrayOf(""),
                        this.filesDir
                    )
                } catch (e: Exception) {
                    Log.e("adx", e.stackTraceToString())
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    stopSelf()
                    return START_NOT_STICKY
                }
                Toast.makeText(this, getString(R.string.service_start_toast), Toast.LENGTH_SHORT)
                    .show()
                startForeground(1, showMotification());
            }

            ShellServiceAction.STOP -> {
                process_thread?.interrupt()
                process_thread = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION") stopForeground(true)
                }
                stopSelf()
                Toast.makeText(this, getString(R.string.service_stop_toast), Toast.LENGTH_SHORT)
                    .show()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (process_thread != null) Log.w("adx", "onDestroy: process_thread is not null")
        process_thread?.interrupt()
        process_thread = null
    }

    private fun runCommand(command: String, envp: Array<String>, dir: File) {
        process_thread = ShellThread(command, envp, dir) { outputBuilder.append(it + "\n") }
        process_thread?.start()
    }

    private fun showMotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }
        val notification = NotificationCompat.Builder(this, "shell_bg")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
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