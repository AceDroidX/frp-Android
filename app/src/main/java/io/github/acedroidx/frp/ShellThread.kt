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
    @Volatile
    private var process: Process? = null

    override fun run() {
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(dir)
            envp.forEach { (key, value) ->
                processBuilder.environment()[key] = value
            }
            processBuilder.redirectErrorStream(true) // 合并错误流

            val runningProcess = processBuilder.start()
            process = runningProcess

            // 处理输出流
            runningProcess.inputStream.bufferedReader().use { reader ->
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
            val exitCode = runningProcess.waitFor()
            outputCallback("Process exited with code: $exitCode")

        } catch (e: Exception) {
            e.printStackTrace()
            outputCallback("Error: ${e.javaClass.simpleName} - ${e.message}")
        } finally {
            stopProcess()
        }
    }

    fun stopProcess() {
        // 进程创建失败时没有可清理的对象，直接返回，避免掩盖真正的启动异常。
        val runningProcess = process ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runningProcess.destroyForcibly()
            } else {
                runningProcess.destroy()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            outputCallback("Error stopping process: ${e.message}")
        }
    }
}
