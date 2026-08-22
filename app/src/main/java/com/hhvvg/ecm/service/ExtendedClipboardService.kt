package com.hhvvg.ecm.service

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.hhvvg.ecm.BuildConfig
import com.hhvvg.ecm.ExtFramework.Companion.clipboardImplName
import com.hhvvg.ecm.IExtClipboardService
import com.hhvvg.ecm.configuration.AutoClearStrategyInfo
import com.hhvvg.ecm.model.ClipboardReadInfo
import com.hhvvg.ecm.configuration.Configuration
import com.hhvvg.ecm.configuration.ExtConfigurationStore
import com.hhvvg.ecm.util.asClass
import com.hhvvg.ecm.util.doAfter
import com.hhvvg.ecm.util.getField
import com.hhvvg.ecm.util.invokeMethod
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author hhvvg
 */
class ExtendedClipboardService(
    private val context: Context,
    private val realClipboardService: Any
) : IExtClipboardService.Stub() {
    companion object {
        const val TAG = "ECM_Service"
        const val bundleBinderKey = "ExtendedClipboardServiceBinder"
        const val intentBundleKey = "ExtendedClipboardServiceBundle"
        const val delayThreadName = "ExtendedClipboardServiceDelayThread"
    }

    private fun log(msg: String) {
        XposedBridge.log("[$TAG] $msg")
    }

    private val mLock = realClipboardService.getField<Any>("mLock")

    private val dataStore by lazy {
        ExtConfigurationStore()
    }

    private val delayExecutor = ScheduledThreadPoolExecutor(1, DelayThreadFactory())
    @Volatile
    private var currentTimeoutTask: ScheduledFuture<*>? = null
    private val currentCountDown = AtomicInteger(0)

    // Test tracking variables
    @Volatile
    private var lastReadPackageName: String = ""
    @Volatile
    private var lastReadTimestamp: Long = 0
    private val totalReadCount = AtomicInteger(0)
    private val readLog = mutableListOf<ClipboardReadInfo>()

    // Store last known userId/deviceId for clear operations
    @Volatile
    private var lastUserId: Int = 0
    @Volatile
    private var lastDeviceId: Int = 0

    private inner class ClearDelayTask(
        private val packageName: String,
        private val userId: Int,
        private val deviceId: Int
    ) : Runnable {
        override fun run() {
            log("Timeout clear triggered for $packageName")
            clearClipboard(packageName, userId, deviceId)
        }
    }

    private class DelayThreadFactory : ThreadFactory {
        override fun newThread(r: Runnable?): Thread {
            return Thread(r, delayThreadName)
        }
    }

    init {
        log("Service init")
        ensureServices()
        resetReadCount()
        log("Service init complete, readCount=${dataStore.autoClearReadCount}, countdown=$currentCountDown")
    }

    private fun ensureServices() {
        provideBinderService()
        provideAutoClearService()
    }

    private fun provideBinderService() {
        val clipImplClazz = clipboardImplName.asClass(context.classLoader) ?: return

        clipImplClazz.doAfter(
            "getPrimaryClip",
            String::class.java,
            String::class.java,
            Int::class.java,
            Int::class.java
        ) {
            val packageName = it.args[0].toString()
            if (packageName == BuildConfig.PACKAGE_NAME) {
                onServiceRequirement(it)
            }
        }
    }

    private fun provideAutoClearService() {
        val clipImplClazz = clipboardImplName.asClass(context.classLoader) ?: return

        clipImplClazz.doAfter(
            "getPrimaryClip",
            String::class.java,
            String::class.java,
            Int::class.java,
            Int::class.java
        ) {
            val packageName = it.args[0] as String
            val userId = it.args[2] as Int
            val deviceId = it.args[3] as Int
            val clipData = it.result as ClipData?
            onPrimaryClipGet(clipData, packageName, userId, deviceId)
        }

        clipImplClazz.doAfter(
            "setPrimaryClip",
            ClipData::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Int::class.java
        ) {
            val data = it.args[0] as ClipData
            val packageName = it.args[1] as String
            val userId = it.args[3] as Int
            val deviceId = it.args[4] as Int
            onClipboardSet(data, packageName, userId, deviceId)
        }

        log("ClipboardService hooks installed successfully")
    }

    private fun onPrimaryClipGet(clipData: ClipData?, packageName: String, userId: Int, deviceId: Int) {
        val enable = dataStore.enable
        val autoClearEnable = dataStore.autoClearEnable
        
        
        
        log("onPrimaryClipGet: pkg=$packageName, enable=$enable, autoClear=$autoClearEnable, clipData=${clipData != null}")
        
        if (!enable) {
            log("  -> skipped: service disabled")
            return
        }
        if (!autoClearEnable) {
            log("  -> skipped: autoClear disabled")
            return
        }
        if (clipData == null) {
            log("  -> skipped: clipData is null")
            return
        }

        lastUserId = userId
        lastDeviceId = deviceId

        val shouldClear = shouldTriggerClear(packageName)
        log("  -> shouldTriggerClear=$shouldClear for pkg=$packageName")
        
        if (shouldClear) {
            lastReadPackageName = packageName
            lastReadTimestamp = System.currentTimeMillis()
            val newCount = totalReadCount.incrementAndGet()
            synchronized(readLog) {
                readLog.add(ClipboardReadInfo(packageName, lastReadTimestamp))
                if (readLog.size > 100) {
                    readLog.removeAt(0)
                }
            }
            log("  -> tracked read, totalReadCount=$newCount")
        } else {
            log("  -> skipped tracking (whitelisted)")
        }

        if (!shouldClear) {
            log("  -> exit: shouldClear=false")
            return
        }

        val content = clipData.getItemAt(0)?.text?.toString()
        log("  -> clipContent='$content'")
        if (content == null) {
            log("  -> skipped: content is null")
            return
        }
        if (clipContentMatchesExclusion(content)) {
            log("  -> skipped: content matches exclusion")
            return
        }

        val strategy = findStrategyForPackage(packageName)
        if (strategy != null) {
            log("  -> found per-app strategy: $strategy")
            handlePerAppStrategy(strategy, packageName, userId, deviceId)
            return
        }

        log("  -> calling handleAutoClear, countdown=$currentCountDown")
        handleAutoClear(packageName, userId, deviceId)
    }

    private fun onClipboardSet(data: ClipData, packageName: String, userId: Int, deviceId: Int) {
        if (!dataStore.enable) return

        log("onClipboardSet: pkg=$packageName")
        
        lastUserId = userId
        lastDeviceId = deviceId

        resetReadCount()
        log("  -> resetReadCount, countdown=$currentCountDown")

        cancelCurrentTimeoutTask()
    }

    /**
     * Find a per-app strategy for the given package.
     * Uses exact match or regex match only (no contains).
     */
    private fun findStrategyForPackage(packageName: String): AutoClearStrategyInfo? {
        for (strategy in dataStore.autoClearStrategy) {
            // Exact match
            if (packageName == strategy.packageName) {
                return strategy
            }
            // Regex match
            try {
                if (packageName.matches(Regex(strategy.packageName))) {
                    return strategy
                }
            } catch (e: Throwable) {
                // Invalid regex, skip
            }
        }
        return null
    }

    private fun handlePerAppStrategy(
        strategy: AutoClearStrategyInfo,
        packageName: String,
        userId: Int,
        deviceId: Int
    ) {
        when {
            strategy.clearFlag and AutoClearStrategyInfo.FLAG_CLEAR_IGNORE != 0 -> {
                return
            }
            strategy.clearFlag and AutoClearStrategyInfo.FLAG_CLEAR_IMMEDIATELY != 0 -> {
                clearClipboard(packageName, userId, deviceId)
            }
            strategy.clearFlag and AutoClearStrategyInfo.FLAG_CLEAR_COUNT != 0 -> {
                if (currentCountDown.decrementAndGet() <= 0) {
                    clearClipboard(packageName, userId, deviceId)
                    resetReadCount()
                }
            }
        }
    }

    /**
     * Determine if auto-clear should trigger for this package based on global mode.
     */
    private fun shouldTriggerClear(packageName: String): Boolean {
        val workMode = dataStore.autoClearWorkMode
        val whitelist = dataStore.autoClearAppWhitelist
        val blacklist = dataStore.autoClearAppBlacklist
        
        val result = when (workMode) {
            Configuration.WORK_MODE_WHITELIST -> {
                val matched = matchesWhitelist(packageName)
                log("shouldTriggerClear: workMode=WHITELIST, pkg=$packageName, whitelist=$whitelist, matched=$matched, result=${!matched}")
                !matched
            }
            Configuration.WORK_MODE_BLACKLIST -> {
                val matched = matchesBlacklist(packageName)
                log("shouldTriggerClear: workMode=BLACKLIST, pkg=$packageName, blacklist=$blacklist, matched=$matched")
                matched
            }
            else -> {
                log("shouldTriggerClear: workMode=$workMode, result=false")
                false
            }
        }
        return result
    }

    private fun handleAutoClear(packageName: String, userId: Int, deviceId: Int) {
        val timeout = dataStore.autoClearTimeout
        val readCount = dataStore.autoClearReadCount

        log("handleAutoClear: pkg=$packageName, timeout=$timeout, readCount=$readCount, countdown=$currentCountDown")

        if (timeout > 0) {
            log("  -> scheduling timeout clear in ${timeout}s")
            scheduleTimeoutClear(packageName, userId, deviceId, timeout)
        }

        if (readCount > 0) {
            val remaining = currentCountDown.decrementAndGet()
            log("  -> countdown decremented, remaining=$remaining")
            if (remaining <= 0) {
                log("  -> TRIGGERING CLEAR NOW!")
                clearClipboard(packageName, userId, deviceId)
                resetReadCount()
                log("  -> resetReadCount, new countdown=$currentCountDown")
            }
        }
    }

    private fun scheduleTimeoutClear(packageName: String, userId: Int, deviceId: Int, timeoutSeconds: Long) {
        cancelCurrentTimeoutTask()
        currentTimeoutTask = delayExecutor.schedule(
            ClearDelayTask(packageName, userId, deviceId),
            timeoutSeconds,
            TimeUnit.SECONDS
        )
    }

    private fun cancelCurrentTimeoutTask() {
        currentTimeoutTask?.cancel(false)
        currentTimeoutTask = null
    }

    private fun clearClipboard(packageName: String, userId: Int, deviceId: Int) {
        try {
            val uid = getIntendingUid(packageName, userId)
            log("clearClipboard: pkg=$packageName, uid=$uid, deviceId=$deviceId")

            mLock?.let { lock ->
                synchronized(lock) {
                    realClipboardService.invokeMethod(
                        "setPrimaryClipInternalLocked",
                        arrayOf(ClipData::class.java, Int::class.java, Int::class.java, String::class.java),
                        null,
                        uid,
                        deviceId,
                        BuildConfig.PACKAGE_NAME
                    )
                }
            } ?: run {
                realClipboardService.invokeMethod(
                    "setPrimaryClipInternal",
                    arrayOf(ClipData::class.java, Int::class.java),
                    null,
                    uid
                )
            }

            log("  -> clipboard cleared successfully")
        } catch (e: Throwable) {
            log("  -> FAILED to clear: ${e.message}")
        }
    }

    private fun getIntendingUid(packageName: String, userId: Int): Int {
        return try {
            realClipboardService.invokeMethod(
                "getIntendingUid",
                arrayOf(String::class.java, Int::class.java),
                packageName,
                userId
            ) as Int
        } catch (e: Throwable) {
            Binder.getCallingUid()
        }
    }

    /**
     * Check if clipboard content matches exclusion patterns.
     * Contains match is valid for content filtering.
     */
    private fun clipContentMatchesExclusion(content: String): Boolean {
        for (item in dataStore.autoClearContentExclusionList) {
            try {
                if (content.contains(item) || content.matches(Regex(item))) {
                    return true
                }
            } catch (e: Throwable) {
                // Invalid regex, skip
            }
        }
        return false
    }

    /**
     * Check if package is in whitelist.
     * Uses exact match or regex match only (no contains).
     */
    private fun matchesWhitelist(packageName: String): Boolean {
        val whitelist = dataStore.autoClearAppWhitelist
        log("matchesWhitelist: pkg=$packageName, whitelist=$whitelist")
        for (item in whitelist) {
            // Exact match
            if (packageName == item) {
                log("  -> EXACT MATCH with '$item'")
                return true
            }
            // Regex match
            try {
                if (packageName.matches(Regex(item))) {
                    log("  -> REGEX MATCH with '$item'")
                    return true
                }
            } catch (e: Throwable) {
                log("  -> regex error for '$item': ${e.message}")
            }
        }
        log("  -> no match")
        return false
    }

    /**
     * Check if package is in blacklist.
     * Uses exact match or regex match only (no contains).
     */
    private fun matchesBlacklist(packageName: String): Boolean {
        val blacklist = dataStore.autoClearAppBlacklist
        log("matchesBlacklist: pkg=$packageName, blacklist=$blacklist")
        for (item in blacklist) {
            // Exact match
            if (packageName == item) {
                log("  -> EXACT MATCH with '$item'")
                return true
            }
            // Regex match
            try {
                if (packageName.matches(Regex(item))) {
                    log("  -> REGEX MATCH with '$item'")
                    return true
                }
            } catch (e: Throwable) {
                log("  -> regex error for '$item': ${e.message}")
            }
        }
        log("  -> no match")
        return false
    }

    private fun resetReadCount() {
        val newCount = dataStore.autoClearReadCount
        currentCountDown.set(newCount)
        log("resetReadCount: set countdown to $newCount")
    }

    private fun onServiceRequirement(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
        try {
            val originalClip = param.result as? ClipData
            val binder = this as IBinder
            val intent = createBinderIntent(binder)
            
            if (originalClip != null && originalClip.itemCount > 0) {
                val newClip = ClipData(originalClip.description, originalClip.getItemAt(0))
                newClip.addItem(ClipData.Item(intent))
                param.result = newClip
            } else {
                val newClip = ClipData.newPlainText("", "")
                newClip.addItem(ClipData.Item(intent))
                param.result = newClip
            }
        } catch (e: Throwable) {
            log("Failed to inject binder: ${e.message}")
        }
    }

    // ===== IExtClipboardService interface implementation =====

    override fun setEnable(enable: Boolean) {
        log("setEnable: $enable")
        dataStore.enable = enable
    }

    override fun isEnable(): Boolean = dataStore.enable

    override fun setAutoClearEnable(enable: Boolean) {
        log("setAutoClearEnable: $enable")
        dataStore.autoClearEnable = enable
    }

    override fun isAutoClearEnable(): Boolean = dataStore.autoClearEnable

    override fun getAutoClearWorkMode(): Int = dataStore.autoClearWorkMode

    override fun setAutoClearWorkMode(mode: Int) {
        log("setAutoClearWorkMode: $mode")
        dataStore.autoClearWorkMode = mode
    }

    override fun getAutoClearReadCount(): Int = dataStore.autoClearReadCount

    override fun setAutoClearReadCount(count: Int) {
        log("setAutoClearReadCount: $count")
        dataStore.autoClearReadCount = count
        resetReadCount()
    }

    override fun setAutoClearAppWhitelist(exclusions: MutableList<String>) {
        log("setAutoClearAppWhitelist: $exclusions")
        dataStore.autoClearAppWhitelist = exclusions
    }

    override fun setAutoClearAppBlacklist(exclusions: MutableList<String>) {
        log("setAutoClearAppBlacklist: $exclusions")
        dataStore.autoClearAppBlacklist = exclusions
    }

    override fun getAutoClearAppBlacklist(): List<String> = dataStore.autoClearAppBlacklist

    override fun getAutoClearAppWhitelist(): List<String> = dataStore.autoClearAppWhitelist

    override fun setAutoClearContentExclusionList(exclusions: List<String>) {
        dataStore.autoClearContentExclusionList = exclusions
    }

    override fun getAutoClearContentExclusionList(): List<String> = dataStore.autoClearContentExclusionList

    override fun setAutoClearTimeout(timeout: Long) {
        log("setAutoClearTimeout: $timeout")
        dataStore.autoClearTimeout = timeout
        if (timeout <= 0) {
            cancelCurrentTimeoutTask()
        }
    }

    override fun getAutoClearTimeout(): Long {
        return dataStore.autoClearTimeout
    }

    override fun getAutoClearStrategies(): List<AutoClearStrategyInfo> = dataStore.autoClearStrategy

    override fun addAutoClearStrategy(strategy: AutoClearStrategyInfo) {
        dataStore.addAutoClearStrategy(strategy)
    }

    override fun removeStrategy(packageName: String) {
        dataStore.removeAutoClearStrategy(packageName)
    }

    // ===== Test tracking methods =====

    override fun getLastReadPackageName(): String = lastReadPackageName

    override fun getLastReadTimestamp(): Long = lastReadTimestamp

    override fun getTotalReadCount(): Int = totalReadCount.get()

    override fun resetTestCounters() {
        log("resetTestCounters")
        lastReadPackageName = ""
        lastReadTimestamp = 0
        totalReadCount.set(0)
        synchronized(readLog) {
            readLog.clear()
        }
    }

    override fun getReadLog(): List<ClipboardReadInfo> {
        synchronized(readLog) {
            return readLog.toList()
        }
    }

    private fun createBinderIntent(binder: IBinder): Intent {
        return Intent().apply {
            val bundle = Bundle()
            bundle.putBinder(bundleBinderKey, binder)
            putExtra(intentBundleKey, bundle)
        }
    }
}
