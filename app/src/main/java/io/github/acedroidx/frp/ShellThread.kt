package io.github.acedroidx.frp

import android.os.Build
import java.io.File
import java.io.InterruptedIOException

class ShellThread(
    val command: List<String>,
    val dir: File,
    val envp: Map<String, String> = emptyMap(),
    val outputCallback: (text: String) -> Unit
) : Thread() {
    private lateinit var process: Process

    override fun run() {
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(dir)
            envp.forEach { (key, value) ->
                processBuilder.environment()[key] = value
            }
            processBuilder.redirectErrorStream(true) // 合并错误流

            process = processBuilder.start()

            // 处理输出流
            process.inputStream.bufferedReader().use { reader ->
                try {
                    var line: String? = null
                    while (!isInterrupted && reader.readLine().also { line = it } != null) {
                        line?.let { outputCallback(it) }
                    }
                } catch (e: InterruptedIOException) {
                    // 线程被中断
                    outputCallback("Thread interrupted: ${e.message}")
                }
            }

            // 等待进程结束并读取退出码
            val exitCode = process.waitFor()
            outputCallback("Process exited with code: $exitCode")

        } catch (e: Exception) {
            e.printStackTrace()
            outputCallback("Error: ${e.javaClass.simpleName} - ${e.message}")
        } finally {
            stopProcess()
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
            outputCallback("Error stopping process: ${e.message}")
        }
    }
}