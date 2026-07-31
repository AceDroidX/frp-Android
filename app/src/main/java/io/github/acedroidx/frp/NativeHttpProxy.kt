package io.github.acedroidx.frp

object NativeHttpProxy {
    init {
        System.loadLibrary("http_proxy")
    }

    /** 成功时返回 null，失败时返回可供日志和通知展示的错误信息。 */
    external fun start(port: Int): String?

    external fun stop()

    external fun isRunning(): Boolean
}
