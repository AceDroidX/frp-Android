package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ConfigActivity : ComponentActivity() {
    private val configEditText = MutableStateFlow("")
    private val isAutoStart = MutableStateFlow(false)
    private lateinit var configFile: File
    private lateinit var preferences: SharedPreferences

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frpConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.extras?.getParcelable("FrpConfig", FrpConfig::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.extras?.getParcelable("FrpConfig")
        }
        if (frpConfig == null) {
            Log.e("adx", "frp config is null")
            Toast.makeText(this, "frp config is null", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        configFile = frpConfig.getFile(this)
        preferences = getSharedPreferences("data", MODE_PRIVATE)
        readConfig()
        readIsAutoStart()

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
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent() {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { saveConfig();closeActivity() }) {
                    Text(stringResource(R.string.saveConfigButton))
                }
                Button(onClick = { closeActivity() }) {
                    Text(stringResource(R.string.dontSaveConfigButton))
                }
                Text(stringResource(R.string.auto_start_switch))
                Switch(checked = isAutoStart.collectAsStateWithLifecycle(false).value,
                    onCheckedChange = {
                        isAutoStart.value = it
                        setAutoStart(it)
                    })
            }
            TextField(
                configEditText.collectAsStateWithLifecycle("").value,
                onValueChange = { configEditText.value = it },
                textStyle = MaterialTheme.typography.bodyMedium.merge(fontFamily = FontFamily.Monospace)
            )
        }
    }

    fun readConfig() {
        if (configFile.exists()) {
            val mReader = configFile.bufferedReader()
            val mRespBuff = StringBuffer()
            val buff = CharArray(1024)
            var ch = 0
            while (mReader.read(buff).also { ch = it } != -1) {
                mRespBuff.append(buff, 0, ch)
            }
            mReader.close()
            configEditText.value = mRespBuff.toString()
        } else {
            Log.e("adx", "config file is not exist")
            Toast.makeText(this, "config file is not exist", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveConfig() {
        configFile.writeText(configEditText.value)
    }

    fun readIsAutoStart() {
        isAutoStart.value =
            preferences.getStringSet("auto_start_frpc_list", emptySet())?.contains(configFile.name)
                ?: false
    }

    fun setAutoStart(isAutoStart: Boolean) {
        val editor = preferences.edit()
        val set = preferences.getStringSet("auto_start_frpc_list", emptySet())?.toMutableSet()
        if (isAutoStart) {
            set?.add(configFile.name)
        } else {
            set?.remove(configFile.name)
        }
        editor.putStringSet("auto_start_frpc_list", set)
        editor.apply()
        Log.d("adx", set.toString())
    }

    fun closeActivity() {
        setResult(RESULT_OK)
        finish()
    }
}