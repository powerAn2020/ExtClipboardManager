package com.hhvvg.ecm.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

abstract class ServiceStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action) {
            ACTION_STATE_CHANGED -> {
                val enable = intent.getBooleanExtra(EXTRA_STATE_ENABLE, false)
                val source = intent.getStringExtra(EXTRA_EVENT_SOURCE) ?: ""
                onServiceStateChanged(enable, source)
            }
        }
    }

    abstract fun onServiceStateChanged(enable: Boolean, source: String)

    companion object {
        const val ACTION_STATE_CHANGED = "com.hhvvg.ecm.service.STATE_CHANGED"
        const val EXTRA_STATE_ENABLE = "EXTRA_STATE_ENABLE"
        const val EXTRA_EVENT_SOURCE = "EXTRA_EVENT_SOURCE"

        fun sendStateChangedBroadcast(context: Context, enable: Boolean, source: String) {
            val intent = Intent(ACTION_STATE_CHANGED)
            intent.putExtra(EXTRA_STATE_ENABLE, enable)
            intent.putExtra(EXTRA_EVENT_SOURCE, source)
            context.sendBroadcast(intent)
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun registerStateChangedReceiver(context: Context, receiver: BroadcastReceiver) {
            val intentFilter = IntentFilter(ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                //在 Android 14 之前，注册广播接收器时不需要指定 RECEIVER_EXPORTED 或 RECEIVER_NOT_EXPORTED
                // 这两个标志。但是从 Android 14 开始，系统对广播接收器的注册有了更严格的安全要求
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            }else{
                context.registerReceiver(receiver, intentFilter)
            }

        }

    }
}
