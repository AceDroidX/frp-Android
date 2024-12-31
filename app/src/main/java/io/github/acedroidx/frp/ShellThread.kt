package io.github.acedroidx.frp

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
            // https://8thlight.com/insights/handling-blocking-threads-in-java
            while (!isInterrupted) {
                if (!reader.ready()) {
                    try {
                        sleep(100)
                        continue
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                reader.readLine()?.let { outputCallback(it) }
            }
            reader.close()
            process.destroy()
//            Log.d("adx","线程关闭")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}