package com.hhvvg.ecm.configuration

/**
 * @author hhvvg
 */
class Configuration{
    var enable: Boolean = false // 全局服务状态管理
    var syncEnable: Boolean = false //同步服务
    var syncWsServer: String = "" //同步服务-服务器地址
    var syncAuthToken: String = "" //同步服务-服务器访问密钥
    var syncPullOnlyEnable: Boolean = false //同步服务-仅拉取
    var syncEncryptionEnable: Boolean = false //同步服务-消息加密
    var syncEncryptionKey: String = "" //同步服务-消息加密-密钥
    var syncEncryptionIV: String = "" //同步服务-消息加密-初始向量
    var autoClearEnable: Boolean = false // 自动清除开关
    var appReadWhiteEnable: Boolean = false // 过滤应用读开关
    var appWriteWhiteEnable: Boolean = false // 过滤应用写开关
    var autoClearStrategies: MutableList<AutoClearStrategyInfo> = mutableListOf()
    var autoClearTimeout: Long = -1
    var workMode: Int = WORK_MODE_WHITELIST
    var readCount: Int = 1
    var autoClearAppBlacklist: MutableList<String> = mutableListOf()
    var autoClearAppWhitelist: MutableList<String> = mutableListOf()
    var autoClearContentExclusionList: MutableList<String> = mutableListOf()
    var appReadWhitelist: MutableList<String> = mutableListOf()
    var appWriteWhitelist: MutableList<String> = mutableListOf()

    companion object {
        const val WORK_MODE_WHITELIST = 0
        const val WORK_MODE_BLACKLIST = 1
        const val READ_MODE = "READ"
        const val WRITE_MODE = "WRITE"
    }
}