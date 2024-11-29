package com.hhvvg.ecm.service

import android.annotation.TargetApi
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.hhvvg.ecm.BuildConfig
import com.hhvvg.ecm.ExtFramework.Companion.clipboardImplName
import com.hhvvg.ecm.ExtFramework.Companion.clipboardServiceName
import com.hhvvg.ecm.IExtClipboardService
import com.hhvvg.ecm.configuration.AutoClearStrategyInfo
import com.hhvvg.ecm.configuration.Configuration
import com.hhvvg.ecm.configuration.ExtConfigurationStore
import com.hhvvg.ecm.util.Logger
import com.hhvvg.ecm.util.asClass
import com.hhvvg.ecm.util.doAfter
import com.hhvvg.ecm.util.getField
import com.hhvvg.ecm.util.invokeMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
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
        const val bundleBinderKey = "ExtendedClipboardServiceBinder"
        const val intentBundleKey = "ExtendedClipboardServiceBundle"
        const val delayThreadName = "ExtendedClipboardServiceDelayThread"
    }

    private val mLock = realClipboardService.getField<Any>("mLock")

    private val dataStore by lazy {
        ExtConfigurationStore()
    }

    private val delayExecutor = ScheduledThreadPoolExecutor(1, DelayThreadFactory())
    private var currentClearTask: Runnable? = null
    private val currentCountDown = AtomicInteger(0)

    private inner class ClearDelayTask(
        private val packageName: String,
        private val callingUserUid: Int
    ) : Runnable {
        override fun run() {
            clearClipboard(packageName, callingUserUid)
        }
    }

    private class DelayThreadFactory : ThreadFactory {
        override fun newThread(r: Runnable?): Thread {
            return Thread(r, delayThreadName)
        }
    }

    init {
        ensureServices()
    }

    private fun ensureServices() {
        provideBinderService()
        provideAutoClearService()
        provideClipboardAccessService()
        provideClipboardSyncService()
    }

    private fun provideAutoClearService() {
        val clipImplClazz = clipboardImplName.asClass(context.classLoader) ?: return
        when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.S_V2 -> {

                clipImplClazz.doAfter("getPrimaryClip", String::class.java, Int::class.java) {
                    val packageName = it.args[0] as String
                    val userId = it.args[1] as Int
                    val clipData = it.result as ClipData?
                    onPrimaryClipGet(clipData, packageName, userId)
                }
                clipImplClazz.doAfter("setPrimaryClip",
                    ClipData::class.java,
                    String::class.java,
                    Int::class.java
                ) {
                    val data = it.args[0] as ClipData
                    val packageName = it.args[1] as String
                    val uid = it.args[2] as Int
                    onClipboardSet(data, packageName, uid)
                }
            }
            Build.VERSION_CODES.TIRAMISU -> {
                clipImplClazz.doAfter("getPrimaryClip",
                    String::class.java,
                    String::class.java,
                    Int::class.java,
                ) {

                    val packageName = it.args[0] as String
                    val userId = it.args[2] as Int
                    val clipData = it.result as ClipData?
                    onPrimaryClipGet(clipData, packageName, userId)
                }
                clipImplClazz.doAfter("setPrimaryClip",
                    ClipData::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.java,
                ) {
                    val data = it.args[0] as ClipData
                    val packageName = it.args[1] as String
                    val uid = it.args[3] as Int
                    onClipboardSet(data, packageName, uid)
                }
            }
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                clipImplClazz.doAfter("getPrimaryClip",
                    String::class.java,
                    String::class.java,
                    Int::class.java,
                    Int::class.java
                ) {
                    val packageName = it.args[0] as String
                    val userId = it.args[2] as Int
                    val clipData = it.result as ClipData?
                    onPrimaryClipGet(clipData, packageName, userId)
                }
                clipImplClazz.doAfter("setPrimaryClip",
                    ClipData::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.java,
                    Int::class.java
                ) {
                    val data = it.args[0] as ClipData
                    val packageName = it.args[1] as String
                    val uid = it.args[3] as Int
                    onClipboardSet(data, packageName, uid)
                }
            }
        }
    }

    /**
     * 提供剪切板同步服务
     */
    private fun provideClipboardSyncService(){

    }
    /**
     * hook剪切板权限，允许的包可以在后台读取剪切板
     */
    private fun provideClipboardAccessService() {
        if (!dataStore.appWriteWhiteEnable) {
            return
        }
        val clipImplClazz = clipboardServiceName.asClass(context.classLoader) ?: return
        when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.S_V2 -> {
                clipImplClazz.doAfter("clipboardAccessAllowed",
                    Int::class.java,// op
                    String::class.java,// callingPackage
                    Int::class.java, // uid
                    Int::class.java, // userId
                    Boolean::class.java // shouldNoteOp
                ) {
                    //通过调用者UID获取包名，然后匹配白名单
                    if(dataStore.appReadWhitelist.contains(it.args[1])){
                        Logger.d("package ${it.args[1]} clipboardAccessAllowed")
                        it.result=true
                    }
                }
            }
            Build.VERSION_CODES.TIRAMISU -> {
                clipImplClazz.doAfter("clipboardAccessAllowed",
                    Int::class.java, // op
                    String::class.java, // callingPackage
                    String::class.java, // attributionTag
                    Int::class.java, // uid
                    Int::class.java, // userId
                    Boolean::class.java // shouldNoteOp
                ) {
                    if(dataStore.appReadWhitelist.contains(it.args[1])){
                        Logger.d("package ${it.args[1]} clipboardAccessAllowed")
                        it.result=true
                    }
                }
            }
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                clipImplClazz.doAfter("clipboardAccessAllowed",
                    Int::class.java, // op
                    String::class.java,// callingPackage
                    String::class.java, // attributionTag
                    Int::class.java, // uid
                    Int::class.java, // userId
                    Int::class.java, // intendingDeviceId
                    Boolean::class.java // shouldNoteOp
                ) {
                    if(dataStore.appReadWhitelist.contains(it.args[1])){
                        Logger.d("package ${it.args[1]} clipboardAccessAllowed")
                        it.result=true
                    }
                }
            }
        }
    }

    private fun onClipboardSet(data: ClipData, packageName: String, userId: Int) {
        resetReadCount()
        if (!dataStore.enable || dataStore.autoClearTimeout <= 0) {
            return
        }
        scheduleAutoClearTimeoutTask(packageName, userId)
    }

    private fun rescheduleCurrentAutoClearTimeoutTask() {
        removeCurrentAutoClearTask()
        currentClearTask?.let {
            delayExecutor.schedule(it, autoClearTimeout, TimeUnit.SECONDS)
        }
    }

    private fun scheduleAutoClearTimeoutTask(packageName: String, userId: Int) {
        removeCurrentAutoClearTask()
        currentClearTask = ClearDelayTask(packageName, userId)
        delayExecutor.schedule(currentClearTask, autoClearTimeout, TimeUnit.SECONDS)
    }

    private fun removeCurrentAutoClearTask() {
        currentClearTask?.let { task -> delayExecutor.remove(task) }
    }

    private fun onServiceRequirement(param: XC_MethodHook.MethodHookParam) {
        var result = param.result as ClipData?
        val item = ClipData.Item(createBinderIntent(this))
        if (result == null) {
            result = ClipData("LabelForExt", arrayOf(), item)
        } else {
            result = ClipData(result)
            result.addItem(item)
        }
        param.result = result
    }

    private fun onPrimaryClipGet(clipData: ClipData?, packageName: String, userId: Int) {
        executeAutoClearIfPossible(clipData, packageName, userId)
    }

    private fun executeAutoClearIfPossible(clipData: ClipData?, packageName: String, userId: Int) {
        Logger.d("package $packageName uid $userId get clip, count down: ${currentCountDown.get()}")
        if (!dataStore.enable || !dataStore.autoClearEnable) {
            return
        }
        if (packageName == BuildConfig.PACKAGE_NAME) {
            return
        }
        if (clipDataExclude(clipData)) {
            return
        }
        when(dataStore.autoClearWorkMode) {
            Configuration.WORK_MODE_WHITELIST -> {
                if (matchesWhitelist(packageName)) {
                    return
                }
                countDownAndClearIfPossible(packageName, userId)
            }
            Configuration.WORK_MODE_BLACKLIST -> {
                if (matchesBlacklist(packageName)) {
                    countDownAndClearIfPossible(packageName, userId)
                }
            }
        }
    }

    //    从13开始剪切板实现类入参改了
// https://android.googlesource.com/platform/frameworks/base.git/+/refs/tags/android-platform-12.1.0_r32/services/core/java/com/android/server/clipboard/ClipboardService.java
// https://android.googlesource.com/platform/frameworks/base.git/+/refs/tags/android-platform-13.0.0_r24/services/core/java/com/android/server/clipboard/ClipboardService.java
// https://android.googlesource.com/platform/frameworks/base.git/+/refs/tags/android-platform-14.0.0_r13/services/core/java/com/android/server/clipboard/ClipboardService.java
    private fun provideBinderService() {
        val clipImplClazz = clipboardImplName.asClass(context.classLoader) ?: return
        when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.S_V2 -> {
                clipImplClazz.doAfter("getPrimaryClip", String::class.java, Int::class.java) {
                    val packageName = it.args[0].toString()
                    if (packageName == BuildConfig.PACKAGE_NAME) {
                        onServiceRequirement(it)
                    }
                }
            }
            Build.VERSION_CODES.TIRAMISU -> {
                clipImplClazz.doAfter("getPrimaryClip",
                    String::class.java,
                    String::class.java,
                    Int::class.java
                ) {
                    val packageName = it.args[0].toString()
                    if (packageName == BuildConfig.PACKAGE_NAME) {
                        onServiceRequirement(it)
                    }
                }
            }
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                clipImplClazz.doAfter("getPrimaryClip",
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
        }
    }

    private fun countDownAndClearIfPossible(packageName: String, userId: Int) {
        if (currentCountDown.get() <= 0) {
            return
        }
        if (currentCountDown.decrementAndGet() <= 0) {
            clearClipboard(packageName, userId)
        }
    }

    private fun clipDataExclude(clipData: ClipData?): Boolean {
        return clipData != null &&
                clipData.itemCount > 0 &&
                clipContentMatchesExclusion(clipData.getItemAt(0).text.toString())
    }

    private fun clipContentMatchesExclusion(content: String): Boolean {
        for (item in dataStore.autoClearContentExclusionList) {
            if (content.contains(item) || content.matches(Regex(item))) {
                return true
            }
        }
        return false
    }

    private fun matchesWhitelist(packageName: String): Boolean {
        for(item in dataStore.autoClearAppWhitelist) {
            if (packageName.contains(item) || packageName.matches(Regex(item))) {
                return true
            }
        }
        return false
    }

    private fun matchesBlacklist(packageName: String): Boolean {
        for(item in dataStore.autoClearAppBlacklist) {
            if (packageName.contains(item) || packageName.matches(Regex(item))) {
                return true
            }
        }
        return false
    }

    private fun clearClipboard(packageName: String, userId: Int) {
        val intendingUid = getIntendingUid(packageName, userId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            clearPrimaryClipSAndLater(packageName, intendingUid)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clearPrimaryClipPAndLater(intendingUid)
        }
    }

    private fun getIntendingUid(packageName: String, userId: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            realClipboardService.invokeMethod(
                "getIntendingUid",
                arrayOf(String::class.java, Int::class.java),
                packageName,
                userId
            ) as Int
        } else {
            Binder.getCallingUid()
        }
    }


    @TargetApi(Build.VERSION_CODES.P)
    private fun clearPrimaryClipPAndLater(intendingUserId: Int) {
        realClipboardService.invokeMethod(
            "setPrimaryClipInternal",
            arrayOf(ClipData::class.java, Int::class.java),
            null,
            intendingUserId
        )
    }

    @TargetApi(Build.VERSION_CODES.S)
    private fun clearPrimaryClipSAndLater(packageName: String, intendingUserId: Int) {
        mLock?.let {
            realClipboardService.invokeMethod(
                "setPrimaryClipInternalLocked",
                arrayOf(ClipData::class.java, Int::class.java, String::class.java),
                null,
                intendingUserId,
                packageName
            )
        }
    }

    override fun setEnable(enable: Boolean) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.enable = enable
        }else{
            Logger.w("permission denied")
        }
    }

    override fun isEnable(): Boolean = dataStore.enable
    override fun setSyncEnable(enable: Boolean) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncEnable = enable
        }else{
            Logger.w("permission denied")
        }
    }

    override fun isSyncPullOnlyEnable(): Boolean =dataStore.syncPullOnlyEnable

    override fun setSyncPullOnlyEnable(enable: Boolean) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncPullOnlyEnable = enable
        }else{
            Logger.w("permission denied")
        }
    }

    override fun isSyncEncryptionEnable(): Boolean = dataStore.syncEncryptionEnable;

    override fun setSyncEncryptionEnable(enable: Boolean) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncEncryptionEnable = enable
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getSyncWsServer(): String? {
        return if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncWsServer
        }else{
            Logger.w("permission denied")
            ""
        }
    }

    override fun setSyncWsServer(addr: String?) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            if (addr?.isNotBlank() == true)
            dataStore.syncWsServer = addr?.let { it }?:""
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getSyncEncryptionKey(): String {
        return if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncEncryptionKey
        }else{
            Logger.w("permission denied")
            ""
        }
    }

    override fun setSyncEncryptionKey(key: String?) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            if (key?.isNotBlank() == true)
            dataStore.syncEncryptionKey = key?.let { it }?:""
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getSyncEncryptionIV(): String {
        return if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncEncryptionIV
        }else{
            Logger.w("permission denied")
            ""
        }
    }

    override fun setSyncEncryptionIV(iv: String?) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            if (iv?.isNotBlank() == true)
            dataStore.syncEncryptionIV = iv?.let { it }?:""
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getSyncAuthToken(): String {
        return if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.syncAuthToken
        }else{
            Logger.w("permission denied")
            ""
        }
    }

    override fun setSyncAuthToken(authToken: String?) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            if (authToken?.isNotBlank() == true)
            dataStore.syncAuthToken = authToken?.let { it }?:""
        }else{
            Logger.w("permission denied")
        }
    }

    override fun isSyncEnable(): Boolean =dataStore.syncEnable

    override fun setReadWhiteEnable(enable: Boolean) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.appReadWhiteEnable = enable
        }else{
            Logger.w("permission denied")
        }
    }

    override fun isReadWhiteEnable(): Boolean =dataStore.appReadWhiteEnable

    override fun setAutoClearEnable(enable: Boolean) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.autoClearEnable = enable
        }else{
            Logger.w("permission denied")
        }
    }

    override fun isAutoClearEnable(): Boolean = dataStore.autoClearEnable

    override fun getAutoClearWorkMode(): Int = dataStore.autoClearWorkMode

    override fun setAutoClearWorkMode(mode: Int) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)) {
            dataStore.autoClearWorkMode = mode
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getAutoClearReadCount(): Int = dataStore.autoClearReadCount

    override fun setAutoClearReadCount(count: Int) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.autoClearReadCount = count
            resetReadCount()
        }else{
            Logger.w("permission denied")
        }
    }

    override fun setAutoClearAppWhitelist(exclusions: MutableList<String>) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.autoClearAppWhitelist = exclusions
        }else{
            Logger.w("permission denied")
        }
    }

    override fun setAutoClearAppBlacklist(exclusions: MutableList<String>) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.autoClearAppBlacklist = exclusions
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getAutoClearAppBlacklist(): List<String> = dataStore.autoClearAppBlacklist

    override fun getAutoClearAppWhitelist(): List<String> = dataStore.autoClearAppWhitelist

    override fun setAutoClearContentExclusionList(exclusions: List<String>) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.autoClearContentExclusionList = exclusions
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getAutoClearContentExclusionList(): List<String> = dataStore.autoClearContentExclusionList

    private fun resetReadCount() {
        currentCountDown.set(dataStore.autoClearReadCount)
    }

    override fun setAutoClearTimeout(timeout: Long) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.autoClearTimeout = timeout
            rescheduleCurrentAutoClearTimeoutTask()
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getAutoClearTimeout(): Long {
        return dataStore.autoClearTimeout
    }

    override fun getAutoClearStrategies(): List<AutoClearStrategyInfo> = dataStore.autoClearStrategy

    override fun addAutoClearStrategy(strategy: AutoClearStrategyInfo) {
        dataStore.addAutoClearStrategy(strategy)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun removeStrategy(packageName: String) {
        dataStore.removeAutoClearStrategy(packageName)
    }

    override fun setAppReadWhitelist(exclusions: List<String>) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.appReadWhitelist = exclusions
        }else{
            Logger.w("permission denied")
        }
    }

    override fun setAppWriteWhitelist(exclusions: List<String>) {
        if (checkCallingOrSelfPermission(BuildConfig.PACKAGE_NAME)){
            dataStore.appWriteWhitelist = exclusions
        }else{
            Logger.w("permission denied")
        }
    }

    override fun getAppReadWhitelist(): List<String> = dataStore.appReadWhitelist

    override fun getAppWriteWhitelist(): List<String> = dataStore.appWriteWhitelist

    private fun createBinderIntent(binder: IBinder): Intent {
        return Intent().apply {
            val bundle = Bundle()
            bundle.putBinder(bundleBinderKey, binder)
            putExtra(intentBundleKey, bundle)
        }
    }
    //增加包名检测，防止其他应用恶意调用
    private fun checkCallingOrSelfPermission(packageName: String): Boolean {
        val callingPid = Binder.getCallingPid()
        val callingUid = Binder.getCallingUid()
        // 通过uid获取包名，需要使用Context的包管理服务
        val packageManager = context.packageManager
        val packages = packageManager.getPackagesForUid(callingUid)
        val callingPackage = if (!packages.isNullOrEmpty()) packages[0] else null
        Log.d("ExtClipboradManager", "调用者PID: $callingPid, 调用者包名: $callingPackage")
        return  if (callingPackage!=null){
            callingPackage == packageName
        }else{
            false
        }
    }
}
