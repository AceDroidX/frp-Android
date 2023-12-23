package io.github.acedroidx.frp

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File


class MainActivity : AppCompatActivity() {
    val filename = "libfrpc.so"
    val frpver = "0.42.0"
    val logname = "frpc.log"
    val configname = "config.ini"

    private lateinit var state_switch: SwitchCompat
    private lateinit var auto_start_switch: SwitchCompat

    private lateinit var mService: ShellService
    private var mBound: Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ShellService.LocalBinder
            mService = binder.getService()
            mBound = true
            state_switch.isChecked = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
            state_switch.isChecked = false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val titleText = findViewById<TextView>(R.id.titleText)
        titleText.text = "frp for Android - ${versionName}/${frpver}"

        checkConfig()
        createBGNotificationChannel()

        mBound = isServiceRunning(ShellService::class.java)
        state_switch = findViewById<SwitchCompat>(R.id.state_switch)
        state_switch.isChecked = mBound
        state_switch.setOnCheckedChangeListener { buttonView, isChecked -> if (isChecked) (startShell()) else (stopShell()) }
        val editor = getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
        auto_start_switch = findViewById<SwitchCompat>(R.id.auto_start_switch)
        auto_start_switch.isChecked = editor.getBoolean("auto_start", false)
        if (auto_start_switch.isChecked) (startShell())
        auto_start_switch.setOnCheckedChangeListener { buttonView, isChecked ->
            val editor = editor.edit()
            editor.putBoolean("auto_start", isChecked)
            editor.apply();
        }
        if (mBound) {
            val intent = Intent(this, ShellService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setListener()
    }

    private fun setListener() {
        val configButton = findViewById<Button>(R.id.configButton)
        configButton.setOnClickListener {
            val intent = Intent(this, ConfigActivity::class.java)
            //intent.putExtra("type",type+"/"+l);
            startActivity(intent)
        }
        val aboutButton = findViewById<Button>(R.id.aboutButton)
        aboutButton.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            readLog()
        }
        val deleteButton = findViewById<Button>(R.id.deleteButton)
        deleteButton.setOnClickListener {
            val logfile = File(this.filesDir.toString() + "/$logname")
            Log.d("adx", logfile.absoluteFile.toString())
            logfile.delete()
            readLog()
        }
    }

    fun readLog() {
        val files: Array<String> = this.fileList()
        val logTextView = findViewById<TextView>(R.id.logTextView)
        if (files.contains(logname)) {
            val mReader = this.openFileInput(logname).bufferedReader()
            val mRespBuff = StringBuffer()
            val buff = CharArray(1024)
            var ch = 0
            while (mReader.read(buff).also { ch = it } != -1) {
                mRespBuff.append(buff, 0, ch)
            }
            mReader.close()
            logTextView.text = mRespBuff.toString()
        } else {
            logTextView.text = "无日志"
        }
    }

    fun checkConfig() {
        val files: Array<String> = this.fileList()
        Log.d("adx", files.joinToString(","))
        if (!files.contains(configname)) {
            val assetmanager = resources.assets
            this.openFileOutput(configname, Context.MODE_PRIVATE).use {
                it.write(assetmanager.open((configname)).readBytes())
            }
        }
    }

    private fun startShell() {
        val intent = Intent(this, ShellService::class.java)
        intent.putExtra("filename", filename)
        startService(intent)
        // Bind to LocalService
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun stopShell() {
        val intent = Intent(this, ShellService::class.java)
        unbindService(connection)
        stopService(intent)
    }

    private fun createBGNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "frp后台服务"
            val descriptionText = "frp后台服务通知"
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel("shell_bg", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}