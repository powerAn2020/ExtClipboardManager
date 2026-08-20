package com.hhvvg.ecm.configuration

import android.os.Handler
import android.os.HandlerThread
import com.google.gson.Gson
import java.io.File

/**
 * @author hhvvg
 */
class ExtConfigurationStore {
    companion object {
        private const val dataDirName = "/data/system/ext_clipboard_manager"
        private const val dataFileName = "ext_clipboard_service_configuration.json"
        private const val workThreadName = "ExtendedClipboardServiceConfigurationWorkThread"
    }

    private val dataDir by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        val dirFile = File(dataDirName)
        if (!dirFile.exists()) {
            dirFile.mkdir()
        }
        dirFile
    }

    private val dataFile by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        val dataFile = File(dataDir, dataFileName)
        if (!dataFile.exists()) {
            dataFile.createNewFile()
        }
        dataFile
    }

    private val workThread by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        HandlerThread(workThreadName).apply {
            start()
        }
    }

    private val workHandler by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(workThread.looper)
    }

    private val gson = Gson()
    private val lock = Any()

    private var configuration: Configuration

    var enable: Boolean
        get() = synchronized(lock) { configuration.enable }
        set(value) {
            synchronized(lock) { configuration.enable = value }
            workHandler.post(this::saveConfiguration)
        }

    var autoClearEnable: Boolean
        get() = synchronized(lock) { configuration.autoClearEnable }
        set(value) {
            synchronized(lock) { configuration.autoClearEnable = value }
            workHandler.post(this::saveConfiguration)
        }

    val autoClearStrategy: List<AutoClearStrategyInfo>
        get() = synchronized(lock) { configuration.autoClearStrategies.toList() }

    var autoClearTimeout: Long
        get() = synchronized(lock) { configuration.autoClearTimeout }
        set(value) {
            synchronized(lock) { configuration.autoClearTimeout = value }
            workHandler.post(this::saveConfiguration)
        }

    var autoClearWorkMode: Int
        get() = synchronized(lock) { configuration.workMode }
        set(value) {
            synchronized(lock) { configuration.workMode = value }
            workHandler.post(this::saveConfiguration)
        }

    var autoClearReadCount: Int
        get() = synchronized(lock) { configuration.readCount }
        set(value) {
            synchronized(lock) { configuration.readCount = value }
            workHandler.post(this::saveConfiguration)
        }

    var autoClearAppBlacklist: List<String>
        get() = synchronized(lock) { configuration.autoClearAppBlacklist.toList() }
        set(value) {
            synchronized(lock) {
                configuration.autoClearAppBlacklist.clear()
                configuration.autoClearAppBlacklist.addAll(value)
            }
            workHandler.post(this::saveConfiguration)
        }

    var autoClearAppWhitelist: List<String>
        get() = synchronized(lock) { configuration.autoClearAppWhitelist.toList() }
        set(value) {
            synchronized(lock) {
                configuration.autoClearAppWhitelist.clear()
                configuration.autoClearAppWhitelist.addAll(value)
            }
            workHandler.post(this::saveConfiguration)
        }

    var autoClearContentExclusionList: List<String>
        get() = synchronized(lock) { configuration.autoClearContentExclusionList.toList() }
        set(value) {
            synchronized(lock) {
                configuration.autoClearContentExclusionList.clear()
                configuration.autoClearContentExclusionList.addAll(value)
            }
            workHandler.post(this::saveConfiguration)
        }

    init {
        configuration = try {
            val json = readFromFile()
            gson.fromJson(json, Configuration::class.java)
        } catch (e: Exception) {
            Configuration()
        }
    }

    fun addAutoClearStrategy(strategyInfo: AutoClearStrategyInfo) {
        synchronized(lock) {
            configuration.autoClearStrategies.add(strategyInfo)
        }
        workHandler.post(this::saveConfiguration)
    }

    fun removeAutoClearStrategy(packageName: String) {
        synchronized(lock) {
            configuration.autoClearStrategies.removeIf {
                it.packageName == packageName
            }
        }
        workHandler.post(this::saveConfiguration)
    }

    private fun readFromFile(): String {
        return dataFile.readText()
    }

    private fun saveConfiguration() {
        synchronized(lock) {
            dataFile.writeText(gson.toJson(configuration))
        }
    }
}
