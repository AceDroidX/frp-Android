package io.github.acedroidx.frp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AutoStartBroadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 代理开关独立于 frp 配置；升级或重启后恢复用户明确开启的代理服务。
                if (preferences.getBoolean(PreferencesKey.HTTP_PROXY_ENABLED, false)) {
                    HttpProxyService.start(context)
                }

                val autoStartOnBoot = preferences.getBoolean(PreferencesKey.AUTO_START, false)
                if (!autoStartOnBoot) return
                val configList = AutoStartHelper.loadAutoStartConfigs(context)
                startShellService(context, configList, ShellServiceAction.START)
            }

            BroadcastAction.START, BroadcastAction.STOP -> {
                val enableBroadcast = when (intent.action) {
                    BroadcastAction.START -> preferences.getBoolean(
                        PreferencesKey.AUTO_START_BROADCAST, false
                    )

                    BroadcastAction.STOP -> preferences.getBoolean(
                        PreferencesKey.AUTO_STOP_BROADCAST, false
                    )

                    else -> false
                }
                if (!enableBroadcast) return

                val allowExtra =
                    preferences.getBoolean(PreferencesKey.AUTO_START_BROADCAST_EXTRA, false)

                val configList = if (allowExtra) {
                    val type =
                        AutoStartHelper.parseType(intent.getStringExtra(BroadcastExtraKey.TYPE))
                    val name = intent.getStringExtra(BroadcastExtraKey.NAME)

                    if (type != null && !name.isNullOrBlank()) {
                        AutoStartHelper.loadAutoStartConfigs(
                            context, typeFilter = type, nameFilter = name
                        )
                    } else {
                        AutoStartHelper.loadAutoStartConfigs(context)
                    }
                } else {
                    AutoStartHelper.loadAutoStartConfigs(context)
                }

                val serviceAction = if (intent.action == BroadcastAction.START) {
                    ShellServiceAction.START
                } else {
                    ShellServiceAction.STOP
                }

                startShellService(context, configList, serviceAction)
            }
        }
    }

    private fun startShellService(
        context: Context, configList: List<FrpConfig>, action: String
    ) {
        if (configList.isEmpty()) return

        val mainIntent = Intent(context, ShellService::class.java)
        mainIntent.action = action
        mainIntent.putParcelableArrayListExtra(IntentExtraKey.FrpConfig, ArrayList(configList))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(mainIntent)
        } else {
            context.startService(mainIntent)
        }
    }
}
