package io.github.acedroidx.frp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class AutoStartBroadReceiver : BroadcastReceiver() {
    private val ACTION = "android.intent.action.BOOT_COMPLETED"
    override fun onReceive(context: Context, intent: Intent) {
        //开机启动
        val editor = context.getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
        val auto_start = editor.getBoolean("auto_start", false)
        if (ACTION == intent.action && auto_start) {
            //开机启动
            val mainIntent = Intent(context, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(mainIntent)
        }
    }


}