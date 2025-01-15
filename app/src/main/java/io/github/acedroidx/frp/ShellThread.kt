package io.github.acedroidx.frp

import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException

class ShellThread(
    val command: String,
    val envp: Array<String>,
    val dir: File,
    val outputCallback: (text: String) -> Unit
) : Thread() {
    private lateinit var process: Process
    override fun run() {
        try {
//            Log.d("adx","线程启动")
            process = Runtime.getRuntime().exec(command, envp, dir)
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
                try {
                    reader.readLine()?.let { outputCallback(it) }
                } catch (e: InterruptedIOException) {
                    // 线程被中断时的处理
//                    Log.d("adx", "读取中断")
                    break
                }
            }
            reader.close()
            stopProcess()
//            Log.d("adx", "线程关闭")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopProcess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly()
            } else {
                process.destroy()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}