package io.github.acedroidx.frp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val isStartup = MutableStateFlow(false)
    private val logText = MutableStateFlow("")
    private val configList = MutableStateFlow<List<String>>(emptyList())
    private val startingConfigList = MutableStateFlow<List<String>>(emptyList())

    private lateinit var receiver: BroadcastReceiver
    private lateinit var preferences: SharedPreferences

    private lateinit var mService: ShellService
    private var mBound: Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ShellService.LocalBinder
            mService = binder.getService()
            mBound = true

            mService.lifecycleScope.launch {
                mService.processThreads.collect { processThreads ->
                    startingConfigList.value = processThreads.keys.toList()
                    autoStartConfigChange()
                }
            }
            mService.lifecycleScope.launch {
                mService.logText.collect {
                    logText.value = it
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver()
        checkConfig()
        updateConfigList()
        checkNotificationPermission()
        createBGNotificationChannel()

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        isStartup.value = preferences.getBoolean("auto_start", false)

        enableEdgeToEdge()
        setContent {
            FrpTheme {
                Scaffold(topBar = {
                    TopAppBar(title = {
                        Text("frp for Android - ${BuildConfig.VERSION_NAME}/${BuildConfig.FrpVersion}")
                    })
                }) { contentPadding ->
                    // Screen content
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .scrollable(orientation = Orientation.Vertical,
                                state = rememberScrollableState { delta -> 0f })
                    ) {
                        MainContent()
                    }
                }
            }
        }

        if (!mBound) {
            val intent = Intent(this, ShellService::class.java)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent() {
        val configList by configList.collectAsStateWithLifecycle(emptyList())
        val clipboardManager = LocalClipboardManager.current
        val startingConfigSetValue by startingConfigList.collectAsStateWithLifecycle()
        val logText by logText.collectAsStateWithLifecycle("")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            configList.filter { it.contains("frpc") }.reversed().forEach { configFileName ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {
                            val intent = Intent(this@MainActivity, ConfigActivity::class.java)
                            intent.putExtra("configFileName", configFileName)
                            startActivity(intent)
                        })
                ) {
                    Text(configFileName)
                    Switch(checked = startingConfigSetValue.contains(configFileName),
                        onCheckedChange = {
                            if (it) (startShell(configFileName)) else (stopShell(configFileName))
                        })
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.auto_start_switch))
                Switch(checked = isStartup.collectAsStateWithLifecycle(false).value,
                    onCheckedChange = {
                        val editor = preferences.edit()
                        editor.putBoolean("auto_start", it)
                        editor.apply()
                        isStartup.value = it
                    })
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = {
                    startActivity(Intent(this@MainActivity, ConfigActivity::class.java))
                }) { Text(stringResource(R.string.addConfigButton)) }
                Button(onClick = {
                    startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                }) { Text(stringResource(R.string.aboutButton)) }
            }
            HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.frp_log), style = MaterialTheme.typography.titleLarge
                )
                Button(onClick = { mService.clearLog() }) { Text(stringResource(R.string.deleteButton)) }
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(logText))
                    // Only show a toast for Android 12 and lower.
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) Toast.makeText(
                        this@MainActivity, getString(R.string.copied), Toast.LENGTH_SHORT
                    ).show()
                }) { Text(stringResource(R.string.copy)) }
            }
            SelectionContainer {
                Text(
                    if (logText == "") stringResource(R.string.no_log) else logText,
                    style = MaterialTheme.typography.bodyMedium.merge(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
        unregisterReceiver(receiver)
    }

    fun checkConfig() {
        val files: Array<String> = this.fileList()
        Log.d("adx", files.joinToString(","))
        if (files.find { it.contains("frpc") } == null) {
            val assetmanager = resources.assets
            this.openFileOutput(BuildConfig.ConfigFileName, MODE_PRIVATE).use {
                it.write(assetmanager.open((BuildConfig.ConfigFileName)).readBytes())
            }
        }
    }

    private fun startShell(configFileName: String) {
        val intent = Intent(this, ShellService::class.java)
        intent.setAction(ShellServiceAction.START)
        intent.putExtra("filename", BuildConfig.FrpcFileName)
        intent.putExtra("configFileName", configFileName)
        startService(intent)
    }

    private fun stopShell(configFileName: String) {
        val intent = Intent(this, ShellService::class.java)
        intent.setAction(ShellServiceAction.STOP)
        intent.putExtra("configFileName", configFileName)
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
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel("shell_bg", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "DELETE_CONFIG") {
                    stopShell(intent.extras?.getString("configFileName")!!)
                    updateConfigList()
                }
                if (intent.action == "ADD_CONFIG") {
                    updateConfigList()
                }
            }
        }
        val filter = IntentFilter("ADD_CONFIG")
        filter.addAction("DELETE_CONFIG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") (registerReceiver(receiver, filter))
        }
    }

    private fun updateConfigList() {
        val files: Array<String> = this.fileList()
        configList.value =
            files.filter { it.contains("frpc") }.sortedBy { it.substringBeforeLast(".") }.reversed()
    }

    private fun autoStartConfigChange() {
        with(preferences.edit()) {
            putStringSet("auto_start_config", startingConfigList.value.toSet())
            apply()
        }
    }
}