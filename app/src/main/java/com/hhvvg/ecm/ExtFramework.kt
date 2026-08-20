package com.hhvvg.ecm

import android.content.Context
import android.os.Build
import com.hhvvg.ecm.service.ExtendedClipboardService
import com.hhvvg.ecm.util.afterConstructor
import com.hhvvg.ecm.util.asClass
import com.hhvvg.ecm.util.setExtraField
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ExtFramework : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        // Android 12-15: com.android.server.clipboard.ClipboardService
        // Android 16+: May have different class structure
        const val clipboardServiceName = "com.android.server.clipboard.ClipboardService"
        const val serviceName = "_extendedClipboardService_injected_by_hhvvg"
        const val clipboardImplName = "$clipboardServiceName\$ClipboardImpl"
        const val staticClipboardServiceName = "_staticExtendedClipboardServiceInstance_injected_by_hhvvg"
    }

    override fun handleLoadPackage(p0: XC_LoadPackage.LoadPackageParam) {
        if (p0.packageName != "android") {
            return
        }
        
        // Try to hook ClipboardService
        try {
            hookClipboardService(p0)
        } catch (e: Throwable) {
            XposedBridge.log("Failed to hook ClipboardService: ${e.message}")
            // Try alternative approach for Android 16+
            tryHookClipboardServiceAlternative(p0)
        }
    }

    private fun hookClipboardService(p0: XC_LoadPackage.LoadPackageParam) {
        clipboardServiceName.asClass(p0.classLoader)?.afterConstructor(Context::class.java) {
            val context = it.args[0] as Context
            val clipboardService = ExtendedClipboardService(context, it.thisObject)
            setExtraField(serviceName, clipboardService)
            XposedBridge.log("Successfully hooked ClipboardService on Android ${Build.VERSION.SDK_INT}")
        }
    }

    private fun tryHookClipboardServiceAlternative(p0: XC_LoadPackage.LoadPackageParam) {
        // Android 16 may have moved ClipboardService to a different package
        // or changed its constructor signature
        val alternativeClassNames = listOf(
            "com.android.server.clipboard.ClipboardService",
            "com.android.server.clipboard.ClipboardManagerService",
            "android.server.clipboard.ClipboardService"
        )
        
        for (className in alternativeClassNames) {
            try {
                val clazz = className.asClass(p0.classLoader) ?: continue
                XposedBridge.log("Found clipboard service class: $className")
                
                // Try different constructor signatures
                val constructors = clazz.declaredConstructors
                for (constructor in constructors) {
                    val paramTypes = constructor.parameterTypes
                    if (paramTypes.isNotEmpty() && Context::class.java.isAssignableFrom(paramTypes[0])) {
                        XposedBridge.log("Hooking constructor with ${paramTypes.size} parameters")
                        constructor.isAccessible = true
                        
                        // Use XposedBridge.hookConstructor
                        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                try {
                                    val context = param.args[0] as Context
                                    val clipboardService = ExtendedClipboardService(context, param.thisObject)
                                    setExtraField(serviceName, clipboardService)
                                    XposedBridge.log("Successfully hooked alternative ClipboardService")
                                } catch (e: Throwable) {
                                    XposedBridge.log("Failed to hook alternative constructor: ${e.message}")
                                }
                            }
                        })
                        return
                    }
                }
            } catch (e: Throwable) {
                XposedBridge.log("Failed to hook $className: ${e.message}")
            }
        }
        
        XposedBridge.log("Could not find any ClipboardService to hook")
    }

    override fun initZygote(p0: IXposedHookZygoteInit.StartupParam) {
        // Do nothing
    }
}

