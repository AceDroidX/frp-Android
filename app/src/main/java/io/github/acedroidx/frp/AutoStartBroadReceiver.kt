package io.github.acedroidx.frp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

class AutoStartBroadReceiver : BroadcastReceiver() {
    private val ACTION = "android.intent.action.BOOT_COMPLETED"
    override fun onReceive(context: Context, intent: Intent) {
        //开机启动
        val editor = context.getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
        val auto_start = editor.getBoolean(PreferencesKey.AUTO_START, false)
        if (ACTION == intent.action && auto_start) {
            val frpcConfigSet = editor.getStringSet(PreferencesKey.AUTO_START_FRPC_LIST, emptySet())
            val frpsConfigSet = editor.getStringSet(PreferencesKey.AUTO_START_FRPS_LIST, emptySet())
            val frpcConfigList = frpcConfigSet?.map { FrpConfig(FrpType.FRPC, it) }
            val frpsConfigList = frpsConfigSet?.map { FrpConfig(FrpType.FRPS, it) }
            val configList = (frpsConfigList ?: emptyList()) + (frpcConfigList ?: emptyList())
            if (configList.isEmpty()) return
            //开机启动
            val mainIntent = Intent(context, ShellService::class.java)
            mainIntent.setAction(ShellServiceAction.START)
            mainIntent.putParcelableArrayListExtra(IntentExtraKey.FrpConfig, ArrayList(configList))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(mainIntent)
            } else {
                context.startService(mainIntent)
            }
        }
    }
}