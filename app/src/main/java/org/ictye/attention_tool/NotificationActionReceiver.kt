package org.ictye.attention_tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, ClockService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
    }
}
