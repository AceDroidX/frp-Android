package io.github.acedroidx.frp

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Random


class ShellService : LifecycleService() {
    private val _processThreads = MutableStateFlow(mutableMapOf<String, ShellThread>())
    val processThreads = _processThreads.asStateFlow()

    private val _logText = MutableStateFlow("")
    val logText: StateFlow<String> = _logText

    fun clearLog() {
        _logText.value = ""
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
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ShellServiceAction.START -> {
                val filename = intent.extras?.getString("filename")
                val configFileName = intent.extras?.getString("configFileName")
                Log.d("adx", "start configFileName is $configFileName")
                if (filename == null) {
                    Log.w("adx", "filename is null,service won't start")
                    Toast.makeText(this, "filename is null,service won't start", Toast.LENGTH_SHORT)
                        .show()
                    return START_NOT_STICKY
                }
                startFrpc(configFileName, filename)
            }

            ShellServiceAction.AUTO_START -> {
                val filename = intent.extras?.getString("filename")
                val preferences = getSharedPreferences("data", MODE_PRIVATE)
                val autoStartConfigSet = preferences.getStringSet("auto_start_config", emptySet())
                autoStartConfigSet?.forEach {
                    startFrpc(it, filename)
                }
            }

            ShellServiceAction.STOP -> {
                val configFileName = intent.extras?.getString("configFileName")
                stopThread(configFileName!!)
                if (_processThreads.value.isEmpty()) {
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
        }
        return START_NOT_STICKY
    }

    private fun startFrpc(configFileName: String?, filename: String?) {
        if (!_processThreads.value.contains(configFileName)) {
            val ainfo = packageManager.getApplicationInfo(
                packageName, PackageManager.GET_SHARED_LIBRARY_FILES
            )
            Log.d("adx", "native library dir ${ainfo.nativeLibraryDir}")
            try {
                val thread = runCommand(
                    "${ainfo.nativeLibraryDir}/${filename} -c ${configFileName}",
                    arrayOf(""),
                    this.filesDir
                )
                _processThreads.update { it.toMutableMap().apply { put(configFileName!!, thread) } }
                Toast.makeText(this, getString(R.string.service_start_toast), Toast.LENGTH_SHORT)
                    .show()
                startForeground(1, showNotification());
            } catch (e: Exception) {
                Log.e("adx", e.stackTraceToString())
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!_processThreads.value.isEmpty()) {
            _processThreads.value.forEach {
                it.value.interrupt()
                it.value.stopProcess()
            }
            _processThreads.update { it.clear();it }
        }
    }

    private fun runCommand(command: String, envp: Array<String>, dir: File): ShellThread {
        val process_thread = ShellThread(command, envp, dir) { _logText.value += it + "\n" }
        process_thread.start()
        return process_thread;
    }

    private fun showNotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }
        val notification = NotificationCompat.Builder(this, "shell_bg")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.frpc_notification_title)).setContentText(
                getString(
                    R.string.frpc_notification_content, _processThreads.value.size
                )
            )
            //.setTicker("test")
            .setContentIntent(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        } else {
            return notification.build()
        }
    }

    fun stopThread(configFileName: String) {
        val thread = _processThreads.value.get(configFileName)
        thread?.interrupt()
        thread?.stopProcess()
        _processThreads.update {
            it.toMutableMap().apply { remove(configFileName) }
        }
        startForeground(1, showNotification());
    }
}