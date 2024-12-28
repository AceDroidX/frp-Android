package io.github.acedroidx.frp

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {
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
            state_switch.isChecked = mService.getIsRunning()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val titleText = findViewById<TextView>(R.id.titleText)
        titleText.text = "frp for Android - ${versionName}/${BuildConfig.FrpVersion}"

        checkConfig()
        checkNotificationPermission()
        createBGNotificationChannel()

        state_switch = findViewById<SwitchCompat>(R.id.state_switch)
        state_switch.isChecked = false
        state_switch.setOnClickListener { if (state_switch.isChecked) (startShell()) else (stopShell()) }
        val preferences = getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
        auto_start_switch = findViewById<SwitchCompat>(R.id.auto_start_switch)
        auto_start_switch.isChecked = preferences.getBoolean("auto_start", false)
        auto_start_switch.setOnCheckedChangeListener { _, isChecked ->
            val editor = preferences.edit()
            editor.putBoolean("auto_start", isChecked)
            editor.apply();
        }
        if (!mBound) {
            val intent = Intent(this, ShellService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
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
            mService.clearOutput()
            readLog()
        }
    }

    fun readLog() {
        val logTextView = findViewById<TextView>(R.id.logTextView)
        if (mBound) {
            logTextView.text = mService.getOutput()
        } else {
            Log.w("adx", "readLog mBound==null")
        }
    }

    fun checkConfig() {
        val files: Array<String> = this.fileList()
        Log.d("adx", files.joinToString(","))
        if (!files.contains(BuildConfig.ConfigFileName)) {
            val assetmanager = resources.assets
            this.openFileOutput(BuildConfig.ConfigFileName, Context.MODE_PRIVATE).use {
                it.write(assetmanager.open((BuildConfig.ConfigFileName)).readBytes())
            }
        }
    }

    private fun startShell() {
        val intent = Intent(this, ShellService::class.java)
        intent.setAction(ShellServiceAction.START)
        intent.putExtra("filename", BuildConfig.FrpcFileName)
        startService(intent)
    }

    private fun stopShell() {
        val intent = Intent(this, ShellService::class.java)
        intent.setAction(ShellServiceAction.STOP)
        startService(intent)
    }

    private fun checkNotificationPermission() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
}