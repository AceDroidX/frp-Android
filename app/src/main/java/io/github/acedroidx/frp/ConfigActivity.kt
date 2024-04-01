package io.github.acedroidx.frp

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText

class ConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val saveConfigButton = findViewById<Button>(R.id.saveConfigButton)
        saveConfigButton.setOnClickListener { saveConfig();finish() }
        val dontSaveConfigButton = findViewById<Button>(R.id.dontSaveConfigButton)
        dontSaveConfigButton.setOnClickListener { finish() }

        readConfig()
    }

    fun readConfig() {
        val files: Array<String> = this.fileList()
        val configEditText = findViewById<EditText>(R.id.configEditText)
        if (files.contains(BuildConfig.ConfigFileName)) {
            val mReader = this.openFileInput(BuildConfig.ConfigFileName).bufferedReader()
            val mRespBuff = StringBuffer()
            val buff = CharArray(1024)
            var ch = 0
            while (mReader.read(buff).also { ch = it } != -1) {
                mRespBuff.append(buff, 0, ch)
            }
            mReader.close()
            configEditText.setText(mRespBuff.toString())
        } else {
            configEditText.setText("")
        }
    }

    fun saveConfig() {
        val configEditText = findViewById<EditText>(R.id.configEditText)
        this.openFileOutput(BuildConfig.ConfigFileName, Context.MODE_PRIVATE).use {
            it.write(configEditText.text.toString().toByteArray())
//            Log.d("adx",configEditText.text.toString())
        }
    }
}