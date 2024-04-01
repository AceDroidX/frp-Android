package io.github.acedroidx.frp

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class ShellThread(
    val command: String,
    val envp: Array<String>,
    val dir: File,
    val outputCallback: (text: String) -> Unit
) : Thread() {
    override fun run() {
        try {
//            Log.d("adx","线程启动")
            val process = Runtime.getRuntime().exec(command, envp, dir)
            val inputStream = process.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (((reader.readLine().also { line = it }) != null) && !isInterrupted) {
//                outputBuilder.insert(0, line).append("\n")
//                outputBuilder.append(line).append("\n")
                line?.let { outputCallback(it) }
            }
            reader.close()
            process.destroy()
//            Log.d("adx","线程关闭")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}