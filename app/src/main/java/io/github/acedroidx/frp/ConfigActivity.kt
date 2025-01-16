package io.github.acedroidx.frp

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import java.io.BufferedReader
import java.io.InputStreamReader

class ConfigActivity : ComponentActivity() {
    private val configEditText = MutableStateFlow("")
    private var configFileName: String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initConfigFileName()
        readConfig()

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
                Button(onClick = { saveConfig();closeActivity(ConfigAction.ADD_CONFIG) }) {
                    Text(stringResource(R.string.saveConfigButton))
                }
                Button(onClick = { closeActivity() }) {
                    Text(stringResource(R.string.dontSaveConfigButton))
                }
                Button(onClick = { deleteConfig();closeActivity(ConfigAction.DELETE_CONFIG) }) {
                    Text(stringResource(R.string.deleteConfigButton))
                }
            }
            TextField(
                configEditText.collectAsStateWithLifecycle("").value,
                onValueChange = { configEditText.value = it },
                textStyle = MaterialTheme.typography.bodyMedium.merge(fontFamily = FontFamily.Monospace)
            )
        }
    }

    fun initConfigFileName() {
        configFileName = intent.getStringExtra("configFileName")
        if (configFileName.isNullOrEmpty()) {
            configFileName = "frpc_" + System.currentTimeMillis() + ".toml"
        }
    }

    fun readConfig() {
        val files: Array<String> = this.fileList()
        if (files.contains(configFileName)) {
            val mReader = this.openFileInput(configFileName).bufferedReader()
            val mRespBuff = StringBuffer()
            val buff = CharArray(1024)
            var ch = 0
            while (mReader.read(buff).also { ch = it } != -1) {
                mRespBuff.append(buff, 0, ch)
            }
            mReader.close()
            configEditText.value = mRespBuff.toString()
        } else {
            configEditText.value = readAssetFile(this, BuildConfig.ConfigFileName)
        }
    }

    fun saveConfig() {
        this.openFileOutput(configFileName, Context.MODE_PRIVATE).use {
            it.write(configEditText.value.toByteArray())
        }
    }

    fun deleteConfig() {
        this.deleteFile(configFileName)
    }

    fun closeActivity(action: String? = null) {
        setResult(RESULT_OK, Intent().apply {
            this.action = action
            this.putExtra("configFileName", configFileName)
        })
        finish()
    }

    private fun readAssetFile(context: Context, fileName: String): String {
        val assetManager = context.assets
        val inputStream = assetManager.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String? = reader.readLine()
        while (line != null) {
            stringBuilder.append(line).append("\n")
            line = reader.readLine()
        }
        reader.close()
        return stringBuilder.toString()
    }
}