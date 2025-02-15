package io.github.acedroidx.frp

import android.content.Context
import java.io.File

enum class FrpType(val typeName: String) {
    FRPC("frpc"), FRPS("frps");

    fun getDir(context: Context): File {
        return File(context.filesDir, this.typeName)
    }

    fun getLibName(): String {
        return when (this) {
            FRPC -> BuildConfig.FrpcFileName
            FRPS -> BuildConfig.FrpsFileName
        }
    }

    fun getAutoStartPreferencesKey(): String {
        return when (this) {
            FRPC -> PreferencesKey.AUTO_START_FRPC_LIST
            FRPS -> PreferencesKey.AUTO_START_FRPS_LIST
        }
    }

    fun getConfigAssetsName(): String {
        return when (this) {
            FRPC -> BuildConfig.FrpcConfigFileName
            FRPS -> BuildConfig.FrpsConfigFileName
        }
    }
}