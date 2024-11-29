package com.hhvvg.ecm.util

import com.hhvvg.ecm.BuildConfig
import de.robv.android.xposed.XposedBridge

class Logger {
    companion object {
        val GLOBAL_TAG: String = "[ExtClipboardManager]"
        fun e(msg: String?) {
            XposedBridge.log("$GLOBAL_TAG [E] $msg")
        }

        fun w(msg: String?) {
            XposedBridge.log("$GLOBAL_TAG [W] $msg")
        }

        fun i(msg: String?) {
            XposedBridge.log("$GLOBAL_TAG [I] $msg")
        }

        fun d(msg: String?) {
            if (BuildConfig.DEBUG) XposedBridge.log("$GLOBAL_TAG [D] $msg")
        }

        fun v(msg: String?) {
            if (BuildConfig.DEBUG) XposedBridge.log("$GLOBAL_TAG [V] $msg")
        }
        fun e(msg: String?,t:Throwable) {
            XposedBridge.log("$GLOBAL_TAG [E] $msg")
            XposedBridge.log(t)
        }
        fun e(t:Throwable) {
            XposedBridge.log(t)
        }
        fun w(msg: String?,t:Throwable) {
            XposedBridge.log("$GLOBAL_TAG [W] $msg")
            XposedBridge.log(t)
        }

        fun i(msg: String?,t:Throwable) {
            XposedBridge.log("$GLOBAL_TAG [I] $msg")
            XposedBridge.log(t)
        }

        fun d(msg: String?,t:Throwable) {
            if (BuildConfig.DEBUG) {
                XposedBridge.log("$GLOBAL_TAG [D] $msg")
                XposedBridge.log(t)
            }

        }

        fun v(msg: String?,t:Throwable) {
            if (BuildConfig.DEBUG){
                XposedBridge.log("$GLOBAL_TAG [V] $msg")
                XposedBridge.log(t)
            }
        }
    }
}